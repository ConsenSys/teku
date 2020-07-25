/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.storage.server.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.stategenerator.StateGenerator;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.hashtree.HashTree;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.events.AnchorPoint;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.util.config.StateStorageMode;

public class FsDatabase implements Database {
  private final MetricsSystem metricsSystem;
  private final FsStorage storage;
  private final StateStorageMode stateStorageMode;
  private final UnsignedLong stateStorageFrequency;

  public FsDatabase(
      final MetricsSystem metricsSystem,
      final FsStorage storage,
      final StateStorageMode stateStorageMode,
      final UnsignedLong stateStorageFrequency) {
    this.metricsSystem = metricsSystem;
    this.storage = storage;
    this.stateStorageMode = stateStorageMode;
    this.stateStorageFrequency = stateStorageFrequency;
  }

  @Override
  public void storeGenesis(final AnchorPoint genesis) {
    // We should only have a single block / state / checkpoint at genesis
    final Checkpoint genesisCheckpoint = genesis.getCheckpoint();
    final BeaconState genesisState = genesis.getState();
    final SignedBeaconBlock genesisBlock = genesis.getBlock();
    try (final FsStorage.Transaction transaction = storage.startTransaction()) {
      transaction.storeJustifiedCheckpoint(genesisCheckpoint);
      transaction.storeBestJustifiedCheckpoint(genesisCheckpoint);
      transaction.storeFinalizedCheckpoint(genesisCheckpoint);

      transaction.storeBlock(genesisBlock, true);
      transaction.storeState(genesisBlock.getRoot(), genesisState);
      transaction.commit();
    }
  }

  @Override
  public void update(final StorageUpdate update) {
    if (update.isEmpty()) {
      return;
    }

    try (final FsStorage.Transaction transaction = storage.startTransaction()) {
      updateFinalizedData(
          update.getFinalizedChildToParentMap(),
          update.getFinalizedBlocks(),
          update.getFinalizedStates(),
          transaction);

      update.getFinalizedCheckpoint().ifPresent(transaction::storeFinalizedCheckpoint);
      update.getJustifiedCheckpoint().ifPresent(transaction::storeJustifiedCheckpoint);
      update.getBestJustifiedCheckpoint().ifPresent(transaction::storeBestJustifiedCheckpoint);

      update.getHotBlocks().values().forEach(block -> transaction.storeBlock(block, false));
      update.getFinalizedBlocks().forEach((root, block) -> transaction.finalizeBlock(block));
      update
          .getDeletedHotBlocks()
          .forEach(
              blockRoot -> {
                if (!update.getFinalizedBlocks().containsKey(blockRoot)) {
                  transaction.deleteBlock(blockRoot);
                  transaction.deleteStateByBlockRoot(blockRoot);
                }
              });
      transaction.storeVotes(update.getVotes());
      update.getStateRoots().forEach(transaction::storeStateRoot);
      // Ensure the latest finalized block and state is always stored
      // TODO: Prune earlier states if storage mode is prune
      update
          .getLatestFinalizedBlockAndState()
          .ifPresent(
              blockAndState ->
                  transaction.storeState(blockAndState.getRoot(), blockAndState.getState()));

      // TODO: Periodically store finalized states
      transaction.commit();
    }
  }

  private void updateFinalizedData(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates,
      final FsStorage.Transaction updater) {
    if (finalizedChildToParentMap.isEmpty()) {
      // Nothing to do
      return;
    }

    final BlockProvider blockProvider =
        BlockProvider.withKnownBlocks(
            roots -> SafeFuture.completedFuture(getHotBlocks(roots)), finalizedBlocks);

    switch (stateStorageMode) {
      case ARCHIVE:
        // Get previously finalized block to build on top of
        final SignedBlockAndState baseBlock = getFinalizedBlockAndState();

        final HashTree blockTree =
            HashTree.builder()
                .rootHash(baseBlock.getRoot())
                .childAndParentRoots(finalizedChildToParentMap)
                .build();

        final AtomicReference<UnsignedLong> lastStateStoredSlot =
            new AtomicReference<>(baseBlock.getSlot());

        final StateGenerator stateGenerator =
            StateGenerator.create(blockTree, baseBlock, blockProvider, finalizedStates);
        // TODO (#2397) - don't join, create synchronous API for synchronous blockProvider
        stateGenerator
            .regenerateAllStates(
                (block, state) -> {
                  updater.finalizeBlock(block);

                  UnsignedLong nextStorageSlot =
                      lastStateStoredSlot.get().plus(stateStorageFrequency);
                  if (state.getSlot().compareTo(nextStorageSlot) >= 0) {
                    updater.storeState(block.getRoot(), state);
                    lastStateStoredSlot.set(state.getSlot());
                  }
                })
            .join();
        break;
      case PRUNE:
        for (Bytes32 root : finalizedChildToParentMap.keySet()) {
          SignedBeaconBlock block =
              blockProvider
                  .getBlock(root)
                  .join()
                  .orElseThrow(() -> new IllegalStateException("Missing finalized block"));
          updater.storeBlock(block, true);
        }
        break;
      default:
        throw new UnsupportedOperationException("Unhandled storage mode: " + stateStorageMode);
    }
  }

  private SignedBlockAndState getFinalizedBlockAndState() {
    final Bytes32 baseBlockRoot = storage.getFinalizedCheckpoint().orElseThrow().getRoot();
    final SignedBeaconBlock baseBlock = storage.getBlockByBlockRoot(baseBlockRoot).orElseThrow();
    final BeaconState baseState =
        storage.getStateByStateRoot(baseBlock.getStateRoot()).orElseThrow();
    return new SignedBlockAndState(baseBlock, baseState);
  }

  @Override
  public Optional<StoreBuilder> createMemoryStore() {
    final Optional<Checkpoint> maybeFinalizedCheckpoint = storage.getFinalizedCheckpoint();
    if (maybeFinalizedCheckpoint.isEmpty()) {
      return Optional.empty();
    }
    final Checkpoint justifiedCheckpoint = storage.getJustifiedCheckpoint().orElseThrow();
    final Checkpoint bestJustifiedCheckpoint = storage.getBestJustifiedCheckpoint().orElseThrow();
    final Checkpoint finalizedCheckpoint = maybeFinalizedCheckpoint.get();
    final BeaconState finalizedState =
        storage.getStateByBlockRoot(finalizedCheckpoint.getRoot()).orElseThrow();
    final SignedBeaconBlock finalizedBlock =
        storage.getBlockByBlockRoot(finalizedCheckpoint.getRoot()).orElse(null);
    checkNotNull(finalizedBlock);
    checkState(
        finalizedBlock.getMessage().getState_root().equals(finalizedState.hash_tree_root()),
        "Latest finalized state does not match latest finalized block");
    final Map<Bytes32, Bytes32> childToParentLookup = storage.getHotBlockChildToParentLookup();
    childToParentLookup.put(finalizedBlock.getRoot(), finalizedBlock.getParent_root());

    final Map<UnsignedLong, VoteTracker> votes = storage.loadVotes();

    return Optional.of(
        StoreBuilder.create()
            .metricsSystem(metricsSystem)
            .time(UnsignedLong.valueOf(Instant.now().getEpochSecond()))
            .genesisTime(finalizedState.getGenesis_time())
            .finalizedCheckpoint(finalizedCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .bestJustifiedCheckpoint(bestJustifiedCheckpoint)
            .childToParentMap(childToParentLookup)
            .latestFinalized(new SignedBlockAndState(finalizedBlock, finalizedState))
            .votes(votes));
  }

  @Override
  public Optional<UnsignedLong> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return storage.getBlockByBlockRoot(blockRoot).map(SignedBeaconBlock::getSlot);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UnsignedLong slot) {
    return storage.getFinalizedBlockBySlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UnsignedLong slot) {
    return storage.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return storage.getBlockByBlockRoot(root);
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(blockRoot -> getSignedBlock(blockRoot).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UnsignedLong startSlot, final UnsignedLong endSlot) {
    return storage.streamFinalizedBlocks(startSlot, endSlot);
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UnsignedLong slot) {
    // TODO: Work out if we really need this.
    return null;
  }

  @Override
  public void addHotStateRoots(
      final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {
    // TODO: This shouldn't be needed - updates should come through StorageUpdate events
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return storage.getSlotAndBlockRootFromStateRoot(stateRoot);
  }

  @Override
  public void pruneHotStateRoots(final List<Bytes32> stateRoots) {
    // TODO: Should tie state pruning into finalization updates
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UnsignedLong maxSlot) {
    return storage.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return Optional.empty();
  }

  @Override
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return Stream.empty();
  }

  @Override
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return Optional.empty();
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {}

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {}

  @Override
  public void putProtoArraySnapshot(final ProtoArraySnapshot protoArray) {}

  @Override
  public void close() {
    storage.close();
  }
}
