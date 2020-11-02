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

package tech.pegasys.teku.storage.store;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BlockAndCheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.events.FinalizedChainData;

class StoreTransactionUpdatesFactory {
  private static final Logger LOG = LogManager.getLogger();

  private final Store baseStore;
  private final StoreTransaction tx;

  private final Map<Bytes32, BlockAndCheckpointEpochs> hotBlocks;
  private final Map<Bytes32, SignedBlockAndState> hotBlockAndStates;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final SignedBlockAndState latestFinalizedBlockAndState;
  private final Set<Bytes32> prunedHotBlockRoots =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  public StoreTransactionUpdatesFactory(
      final Store baseStore,
      final StoreTransaction tx,
      final SignedBlockAndState latestFinalizedBlockAndState) {
    this.baseStore = baseStore;
    this.tx = tx;
    this.latestFinalizedBlockAndState = latestFinalizedBlockAndState;
    // Save copy of tx data that may be pruned
    hotBlocks =
        tx.blockAndStates.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        new BlockAndCheckpointEpochs(
                            entry.getValue().getBlock(),
                            CheckpointEpochs.fromBlockAndState(entry.getValue()))));
    hotBlockAndStates = new ConcurrentHashMap<>(tx.blockAndStates);
    stateRoots = new ConcurrentHashMap<>(tx.stateRoots);
  }

  public static StoreTransactionUpdates create(
      final Store baseStore, final StoreTransaction tx, final SignedBlockAndState latestFinalized) {
    return new StoreTransactionUpdatesFactory(baseStore, tx, latestFinalized).build();
  }

  public StoreTransactionUpdates build() {
    // If a new checkpoint has been finalized, calculated what to finalize and what to prune
    final Checkpoint prevFinalizedCheckpoint = baseStore.getFinalizedCheckpoint();
    final Optional<Checkpoint> newFinalizedCheckpoint =
        Optional.of(tx.getFinalizedCheckpoint())
            .filter(c -> c.getEpoch().compareTo(prevFinalizedCheckpoint.getEpoch()) > 0);

    return newFinalizedCheckpoint
        .map(this::buildFinalizedUpdates)
        .orElseGet(() -> createStoreTransactionUpdates(Optional.empty()));
  }

  private StoreTransactionUpdates buildFinalizedUpdates(final Checkpoint finalizedCheckpoint) {
    final Map<Bytes32, Bytes32> finalizedChildToParent =
        collectFinalizedRoots(baseStore, latestFinalizedBlockAndState.getRoot());
    Set<SignedBeaconBlock> finalizedBlocks = collectFinalizedBlocks(tx, finalizedChildToParent);
    Map<Bytes32, BeaconState> finalizedStates = collectFinalizedStates(tx, finalizedChildToParent);

    // Prune collections
    calculatePrunedHotBlockRoots();
    prunedHotBlockRoots.forEach(hotBlocks::remove);
    prunedHotBlockRoots.forEach(hotBlockAndStates::remove);

    final Optional<FinalizedChainData> finalizedChainData =
        Optional.of(
            FinalizedChainData.builder()
                .finalizedCheckpoint(finalizedCheckpoint)
                .latestFinalizedBlockAndState(latestFinalizedBlockAndState)
                .finalizedChildAndParent(finalizedChildToParent)
                .finalizedBlocks(finalizedBlocks)
                .finalizedStates(finalizedStates)
                .build());

    return createStoreTransactionUpdates(finalizedChainData);
  }

  /**
   * Pull subset of hot states that sit at epoch boundaries to persist
   *
   * @return map of hot states to persist by block root
   */
  private Map<Bytes32, BeaconState> getHotStatesToPersist() {
    final Map<Bytes32, BeaconState> statesToPersist =
        hotBlockAndStates.entrySet().stream()
            .filter(
                e ->
                    baseStore.shouldPersistState(
                        e.getValue().getSlot(), e.getValue().getParentRoot()))
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getState()));
    if (statesToPersist.size() > 0) {
      LOG.trace("Persist {} hot states", statesToPersist.size());
    }
    return statesToPersist;
  }

  private Map<Bytes32, Bytes32> collectFinalizedRoots(
      final Store baseStore, final Bytes32 newlyFinalizedBlockRoot) {

    final HashMap<Bytes32, Bytes32> childToParent = new HashMap<>();

    // Add any new blocks that were immediately finalized
    Bytes32 finalizedChainHeadRoot = newlyFinalizedBlockRoot;
    while (!baseStore.containsBlock(finalizedChainHeadRoot)) {
      // Blocks from the new transaction must all be in memory as they haven't been stored yet
      final SignedBeaconBlock block = hotBlocks.get(finalizedChainHeadRoot).getBlock();
      childToParent.put(finalizedChainHeadRoot, block.getParent_root());
      finalizedChainHeadRoot = block.getParent_root();
    }

    // Add existing hot blocks that are now finalized
    if (baseStore.forkChoiceStrategy.contains(newlyFinalizedBlockRoot)) {
      baseStore.forkChoiceStrategy.processHashesInChain(
          newlyFinalizedBlockRoot,
          (blockRoot, slot, parentRoot) -> childToParent.put(blockRoot, parentRoot));
    }
    return childToParent;
  }

  private Set<SignedBeaconBlock> collectFinalizedBlocks(
      final StoreTransaction tx, final Map<Bytes32, Bytes32> finalizedChildToParent) {
    return finalizedChildToParent.keySet().stream()
        .flatMap(root -> tx.getBlockIfAvailable(root).stream())
        .collect(Collectors.toSet());
  }

  private Map<Bytes32, BeaconState> collectFinalizedStates(
      final StoreTransaction tx, final Map<Bytes32, Bytes32> finalizedChildToParent) {
    final Map<Bytes32, BeaconState> states = new HashMap<>();
    for (Bytes32 finalizedRoot : finalizedChildToParent.keySet()) {
      tx.getBlockStateIfAvailable(finalizedRoot)
          .ifPresent(state -> states.put(finalizedRoot, state));
    }
    return states;
  }

  private void calculatePrunedHotBlockRoots() {
    final SignedBeaconBlock finalizedBlock = tx.getLatestFinalizedBlockAndState().getBlock();
    tx.blockAndStates.values().stream()
        // Iterate new blocks in slot order to guarantee we see parents first
        .sorted(Comparator.comparing(SignedBlockAndState::getSlot))
        .filter(
            newBlockAndState ->
                shouldPrune(
                    finalizedBlock,
                    newBlockAndState.getRoot(),
                    newBlockAndState.getSlot(),
                    newBlockAndState.getParentRoot()))
        .forEach(newBlockAndState -> prunedHotBlockRoots.add(newBlockAndState.getRoot()));

    baseStore.forkChoiceStrategy.forEach(
        (blockRoot, slot, parentRoot) -> {
          if (shouldPrune(finalizedBlock, blockRoot, slot, parentRoot)) {
            prunedHotBlockRoots.add(blockRoot);
          }
        });
  }

  private boolean shouldPrune(
      final SignedBeaconBlock finalizedBlock,
      final Bytes32 blockRoot,
      final UInt64 slot,
      final Bytes32 parentRoot) {
    return (slot.isLessThanOrEqualTo(finalizedBlock.getSlot())
            || prunedHotBlockRoots.contains(parentRoot))
        // Keep the actual finalized block
        && !blockRoot.equals(finalizedBlock.getRoot());
  }

  private StoreTransactionUpdates createStoreTransactionUpdates(
      final Optional<FinalizedChainData> finalizedChainData) {
    return new StoreTransactionUpdates(
        tx,
        finalizedChainData,
        hotBlocks,
        hotBlockAndStates,
        getHotStatesToPersist(),
        prunedHotBlockRoots,
        stateRoots);
  }
}
