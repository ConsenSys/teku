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

package tech.pegasys.teku.validator.coordinator.performance;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker.BLOCK_PERFORMANCE_EVALUATION_INTERVAL;

import com.google.common.eventbus.EventBus;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class PerformanceTrackerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final StatusLogger log = mock(StatusLogger.class);
  private final RecentChainData recentChainData =
      MemoryOnlyRecentChainData.create(mock(EventBus.class));
  private PerformanceTracker performanceTracker = new PerformanceTracker(recentChainData, log);
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(64);
  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  private final ChainUpdater chainUpdater = new ChainUpdater(recentChainData, chainBuilder);

  @BeforeAll
  static void setUp() {
    Constants.SLOTS_PER_EPOCH = 4;
  }

  @BeforeEach
  void beforeEach() {
    chainUpdater.initializeGenesis();
  }

  @Test
  void shouldDisplayPerfectBlockInclusion() {
    chainUpdater.advanceChainUntil(10);
    performanceTracker.saveSentBlock(chainUpdater.chainBuilder.getBlockAtSlot(1));
    performanceTracker.saveSentBlock(chainUpdater.chainBuilder.getBlockAtSlot(2));
    performanceTracker.onSlot(compute_start_slot_at_epoch(BLOCK_PERFORMANCE_EVALUATION_INTERVAL));
    BlockPerformance expectedBlockPerformance = new BlockPerformance(2, 2);
    verify(log).performance(expectedBlockPerformance.toString());
  }

  @Test
  void shouldDisplayOneMissedBlock() {
    chainUpdater.advanceChainUntil(10);
    performanceTracker.saveSentBlock(chainUpdater.chainBuilder.getBlockAtSlot(1));
    performanceTracker.saveSentBlock(chainUpdater.chainBuilder.getBlockAtSlot(2));
    performanceTracker.saveSentBlock(dataStructureUtil.randomSignedBeaconBlock(3));
    performanceTracker.onSlot(compute_start_slot_at_epoch(BLOCK_PERFORMANCE_EVALUATION_INTERVAL));
    BlockPerformance expectedBlockPerformance = new BlockPerformance(2, 3);
    verify(log).performance(expectedBlockPerformance.toString());
  }

  @Test
  void shouldDisplayPerfectAttestationInclusion() {
    chainUpdater.advanceChainUntil(1);

    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(2)
            .filter(
                a ->
                    a.getData()
                        .getBeacon_block_root()
                        .equals(chainBuilder.getBlockAtSlot(1).getMessage().hash_tree_root()))
            .findFirst()
            .get();
    block1Options.addAttestation(attestation1);
    SignedBlockAndState latestBlockAndState = chainBuilder.generateBlockAtSlot(2, block1Options);
    chainUpdater.saveBlock(latestBlockAndState);
    chainUpdater.updateBestBlock(latestBlockAndState);

    performanceTracker.saveSentAttestation(attestation1);
    performanceTracker.onSlot(compute_start_slot_at_epoch(UInt64.valueOf(2)));
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(1, 1, 1, 1, 1, 1, 1);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  @Test
  void shouldDisplayInclusionDistanceOfMax2Min1() {
    chainUpdater.advanceChainUntil(1);

    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(2)
            .filter(
                a ->
                    a.getData()
                        .getBeacon_block_root()
                        .equals(chainBuilder.getBlockAtSlot(1).getRoot()))
            .findFirst()
            .get();
    block1Options.addAttestation(attestation1);
    SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(2, block1Options);
    chainUpdater.saveBlock(blockAndState1);
    chainUpdater.updateBestBlock(blockAndState1);

    ChainBuilder.BlockOptions block2Options = ChainBuilder.BlockOptions.create();
    Attestation attestation2 =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(4)
            .filter(
                a ->
                    a.getData()
                        .getBeacon_block_root()
                        .equals(chainBuilder.getBlockAtSlot(2).getRoot()))
            .findFirst()
            .get();
    block2Options.addAttestation(attestation2);
    SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(4, block2Options);
    chainUpdater.saveBlock(blockAndState2);
    chainUpdater.updateBestBlock(blockAndState2);

    performanceTracker.saveSentAttestation(attestation1);
    performanceTracker.saveSentAttestation(attestation2);
    performanceTracker.onSlot(compute_start_slot_at_epoch(UInt64.valueOf(2)));
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(2, 2, 2, 1, 1.5, 2, 2);
    verify(log).performance(expectedAttestationPerformance.toString());
  }
}
