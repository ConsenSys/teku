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

package tech.pegasys.teku.storage.server.sql;

import static tech.pegasys.teku.storage.server.sql.CheckpointType.BEST_JUSTIFIED;
import static tech.pegasys.teku.storage.server.sql.CheckpointType.FINALIZED;
import static tech.pegasys.teku.storage.server.sql.CheckpointType.JUSTIFIED;
import static tech.pegasys.teku.storage.server.sql.CheckpointType.WEAK_SUBJECTIVITY;

import com.google.errorprone.annotations.MustBeClosed;
import java.time.Instant;
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.events.AnchorPoint;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.util.config.StateStorageMode;

public class SqlDatabase implements Database {
  private final MetricsSystem metricsSystem;
  private final SqlStorage storage;
  private final SqlChainStorage chainStorage;
  private final StateStorageMode stateStorageMode;
  private final UInt64 stateStorageFrequency;

  public SqlDatabase(
      final MetricsSystem metricsSystem,
      final SqlStorage storage,
      final SqlChainStorage chainStorage,
      final StateStorageMode stateStorageMode,
      final UInt64 stateStorageFrequency) {
    this.metricsSystem = metricsSystem;
    this.storage = storage;
    this.chainStorage = chainStorage;
    this.stateStorageMode = stateStorageMode;
    this.stateStorageFrequency = stateStorageFrequency;
  }

  @Override
  public void storeGenesis(final AnchorPoint genesis) {
    // We should only have a single block / state / checkpoint at genesis
    final Checkpoint genesisCheckpoint = genesis.getCheckpoint();
    final BeaconState genesisState = genesis.getState();
    final SignedBeaconBlock genesisBlock = genesis.getBlock();
    try (final SqlChainStorage.Transaction transaction = chainStorage.startTransaction()) {
      transaction.storeCheckpoint(CheckpointType.JUSTIFIED, genesisCheckpoint);
      transaction.storeCheckpoint(CheckpointType.BEST_JUSTIFIED, genesisCheckpoint);
      transaction.storeCheckpoint(FINALIZED, genesisCheckpoint);

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

    try (final SqlChainStorage.Transaction transaction = chainStorage.startTransaction()) {
      updateFinalizedData(
          update.getFinalizedChildToParentMap(),
          update.getFinalizedBlocks(),
          update.getFinalizedStates(),
          transaction);

      update
          .getFinalizedCheckpoint()
          .ifPresent(checkpoint -> transaction.storeCheckpoint(FINALIZED, checkpoint));
      update
          .getJustifiedCheckpoint()
          .ifPresent(
              checkpoint -> transaction.storeCheckpoint(CheckpointType.JUSTIFIED, checkpoint));
      update
          .getBestJustifiedCheckpoint()
          .ifPresent(
              checkpoint -> transaction.storeCheckpoint(CheckpointType.BEST_JUSTIFIED, checkpoint));

      update.getHotBlocks().values().forEach(block -> transaction.storeBlock(block, false));
      update
          .getDeletedHotBlocks()
          .forEach(
              blockRoot -> {
                if (!update.getFinalizedBlocks().containsKey(blockRoot)) {
                  transaction.deleteHotBlockByBlockRoot(blockRoot);
                  transaction.deleteStateByBlockRoot(blockRoot);
                }
              });
      transaction.storeVotes(update.getVotes());
      update.getStateRoots().forEach(transaction::storeStateRoot);
      update
          .getHotStates()
          .forEach(
              (blockRoot, state) -> {
                if (!update.getFinalizedBlocks().containsKey(blockRoot)) {
                  transaction.storeState(blockRoot, state);
                }
              });

      // Ensure the latest finalized block and state is always stored
      update
          .getLatestFinalizedBlockAndState()
          .ifPresent(
              blockAndState ->
                  transaction.storeState(blockAndState.getRoot(), blockAndState.getState()));
      transaction.commit();
    }
  }

  @Override
  public void updateWeakSubjectivityState(final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    try (final SqlChainStorage.Transaction updater = chainStorage.startTransaction()) {
      weakSubjectivityUpdate
          .getWeakSubjectivityCheckpoint()
          .ifPresentOrElse(
              checkpoint -> updater.storeCheckpoint(CheckpointType.WEAK_SUBJECTIVITY, checkpoint),
              () -> updater.clearCheckpoint(CheckpointType.WEAK_SUBJECTIVITY));
      updater.commit();
    }
  }

  private void updateFinalizedData(
      Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates,
      final SqlChainStorage.Transaction updater) {
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

        final AtomicReference<UInt64> lastStateStoredSlot =
            new AtomicReference<>(baseBlock.getSlot());

        final StateGenerator stateGenerator =
            StateGenerator.create(blockTree, baseBlock, blockProvider, finalizedStates);
        // TODO (#2397) - don't join, create synchronous API for synchronous blockProvider
        stateGenerator
            .regenerateAllStates(
                (block, state) -> {
                  updater.finalizeBlock(block);

                  UInt64 nextStorageSlot = lastStateStoredSlot.get().plus(stateStorageFrequency);
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
          updater.deleteStateByBlockRoot(root);
        }
        break;
      default:
        throw new UnsupportedOperationException("Unhandled storage mode: " + stateStorageMode);
    }
  }

  private SignedBlockAndState getFinalizedBlockAndState() {
    final Bytes32 baseBlockRoot = chainStorage.getCheckpoint(FINALIZED).orElseThrow().getRoot();
    // TODO: Could introduce a getBlockAndStateByBlockRoot
    final SignedBeaconBlock baseBlock = storage.getBlockByBlockRoot(baseBlockRoot).orElseThrow();
    final BeaconState baseState = chainStorage.getStateByBlockRoot(baseBlockRoot).orElseThrow();
    return new SignedBlockAndState(baseBlock, baseState);
  }

  @Override
  public Optional<StoreBuilder> createMemoryStore() {
    final Optional<Checkpoint> maybeFinalizedCheckpoint = chainStorage.getCheckpoint(FINALIZED);
    if (maybeFinalizedCheckpoint.isEmpty()) {
      return Optional.empty();
    }
    final Checkpoint justifiedCheckpoint = chainStorage.getCheckpoint(JUSTIFIED).orElseThrow();
    final Checkpoint bestJustifiedCheckpoint =
        chainStorage.getCheckpoint(BEST_JUSTIFIED).orElseThrow();
    final Checkpoint finalizedCheckpoint = maybeFinalizedCheckpoint.get();
    final Bytes32 finalizedBlockRoot = finalizedCheckpoint.getRoot();
    final BeaconState finalizedState =
        chainStorage.getStateByBlockRoot(finalizedBlockRoot).orElseThrow();
    final SignedBeaconBlock finalizedBlock =
        storage.getBlockByBlockRoot(finalizedBlockRoot).orElseThrow();

    final Map<Bytes32, Bytes32> childToParentLookup = storage.getHotBlockChildToParentLookup();
    childToParentLookup.put(finalizedBlock.getRoot(), finalizedBlock.getParent_root());

    final Map<Bytes32, UInt64> rootToSlotMap = storage.getBlockRootToSlotLookup();
    rootToSlotMap.put(finalizedBlock.getRoot(), finalizedBlock.getSlot());

    final Map<UInt64, VoteTracker> votes = chainStorage.getVotes();

    return Optional.of(
        StoreBuilder.create()
            .metricsSystem(metricsSystem)
            .time(UInt64.valueOf(Instant.now().getEpochSecond()))
            .genesisTime(finalizedState.getGenesis_time())
            .finalizedCheckpoint(finalizedCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .bestJustifiedCheckpoint(bestJustifiedCheckpoint)
            .childToParentMap(childToParentLookup)
            .rootToSlotMap(rootToSlotMap)
            .latestFinalized(new SignedBlockAndState(finalizedBlock, finalizedState))
            .votes(votes));
  }

  @Override
  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return chainStorage.getCheckpoint(WEAK_SUBJECTIVITY);
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return chainStorage.getVotes();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return storage.getBlockByBlockRoot(blockRoot).map(SignedBeaconBlock::getSlot);
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    // TODO: Implement this
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return chainStorage.getFinalizedBlockBySlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return chainStorage.getLatestFinalizedBlockAtSlot(slot);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return storage.getBlockByBlockRoot(root);
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return chainStorage.getHotStateByBlockRoot(root);
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return blockRoots.stream()
        .flatMap(blockRoot -> getHotBlock(blockRoot).stream())
        .collect(Collectors.toMap(SignedBeaconBlock::getRoot, Function.identity()));
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return storage.getBlockByBlockRoot(blockRoot, true);
  }

  @Override
  @MustBeClosed
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return storage.streamFinalizedBlocks(startSlot, endSlot);
  }

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return chainStorage.getSlotAndBlockRootByStateRoot(stateRoot);
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return chainStorage.getLatestAvailableFinalizedState(maxSlot);
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return storage.getMinGenesisTimeBlock();
  }

  @Override
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return storage.streamDepositsFromBlocks();
  }

  @Override
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return Optional.empty();
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    try (final SqlStorage.Transaction updater = storage.startTransaction()) {
      updater.addMinGenesisTimeBlock(event);
      updater.commit();
    }
  }

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
    try (final SqlStorage.Transaction updater = storage.startTransaction()) {
      updater.addDepositsFromBlockEvent(event);
      updater.commit();
    }
  }

  @Override
  public void putProtoArraySnapshot(final ProtoArraySnapshot protoArray) {}

  @Override
  public void close() {
    storage.close();
  }
}
