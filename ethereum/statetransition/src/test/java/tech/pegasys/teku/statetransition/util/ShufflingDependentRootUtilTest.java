/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ShufflingDependentRootUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
      mock(ReadOnlyForkChoiceStrategy.class);
  private final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 stateDependentRoot = dataStructureUtil.randomBytes32();
  // Minimal preset: 8 slots per epoch, so the inclusion list slot 64 (epoch 8) has its shuffling
  // dependent block at slot 55
  private final BeaconState state =
      dataStructureUtil
          .randomBeaconState(UInt64.valueOf(65))
          .updated(mutableState -> mutableState.getBlockRoots().setElement(55, stateDependentRoot));

  @Test
  void usesInclusionListEpochAtProposalEpochBoundary() {
    final Bytes32 previousDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDependentRoot = dataStructureUtil.randomBytes32();
    final BeaconState state =
        dataStructureUtil
            .randomBeaconState(UInt64.valueOf(64))
            .updated(
                mutableState -> {
                  mutableState.getBlockRoots().setElement(47, previousDependentRoot);
                  mutableState.getBlockRoots().setElement(55, currentDependentRoot);
                });

    assertThat(
            ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.valueOf(63)))
        .isEqualTo(previousDependentRoot);
    assertThat(
            ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.valueOf(64)))
        .isEqualTo(currentDependentRoot);
  }

  @Test
  void usesGenesisRootForInitialEpochs() {
    final Bytes32 genesisRoot = dataStructureUtil.randomBytes32();
    final BeaconState state =
        dataStructureUtil
            .randomBeaconState(UInt64.valueOf(8))
            .updated(mutableState -> mutableState.getBlockRoots().setElement(0, genesisRoot));

    assertThat(ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.ZERO))
        .isEqualTo(genesisRoot);
    assertThat(ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.valueOf(8)))
        .isEqualTo(genesisRoot);
  }

  @Test
  void getInclusionListKey_usesPreviousSlotAndForkChoiceDependentRoot() {
    final Bytes32 forkChoiceDependentRoot = dataStructureUtil.randomBytes32();
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(55)))
        .thenReturn(Optional.of(forkChoiceDependentRoot));

    assertThat(
            ShufflingDependentRootUtil.getInclusionListKey(
                spec, Optional.of(forkChoiceStrategy), parentRoot, state, UInt64.valueOf(65)))
        .isEqualTo(new SlotAndBlockRoot(UInt64.valueOf(64), forkChoiceDependentRoot));
  }

  @Test
  void getInclusionListKey_fallsBackToStateWhenForkChoiceDoesNotKnowDependentBlock() {
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(55)))
        .thenReturn(Optional.empty());

    assertThat(
            ShufflingDependentRootUtil.getInclusionListKey(
                spec, Optional.of(forkChoiceStrategy), parentRoot, state, UInt64.valueOf(65)))
        .isEqualTo(new SlotAndBlockRoot(UInt64.valueOf(64), stateDependentRoot));
  }

  @Test
  void getInclusionListKey_fallsBackToStateWithoutForkChoice() {
    assertThat(
            ShufflingDependentRootUtil.getInclusionListKey(
                spec, Optional.empty(), parentRoot, state, UInt64.valueOf(65)))
        .isEqualTo(new SlotAndBlockRoot(UInt64.valueOf(64), stateDependentRoot));
    assertThat(ShufflingDependentRootUtil.getInclusionListKey(spec, state, UInt64.valueOf(65)))
        .isEqualTo(new SlotAndBlockRoot(UInt64.valueOf(64), stateDependentRoot));
  }

  @Test
  void getInclusionListKey_usesInclusionListEpochAtProposalEpochBoundary() {
    final Bytes32 previousEpochDependentRoot = dataStructureUtil.randomBytes32();
    final BeaconState boundaryState =
        state.updated(
            mutableState ->
                mutableState.getBlockRoots().setElement(47, previousEpochDependentRoot));

    // A proposal in slot 64 (epoch 8) uses the inclusion lists of slot 63 (epoch 7)
    assertThat(
            ShufflingDependentRootUtil.getInclusionListKey(spec, boundaryState, UInt64.valueOf(64)))
        .isEqualTo(new SlotAndBlockRoot(UInt64.valueOf(63), previousEpochDependentRoot));
  }

  @Test
  void usesLatestHeaderWhenStateIsAtGenesis() {
    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.ZERO);

    assertThat(ShufflingDependentRootUtil.getShufflingDependentRoot(spec, state, UInt64.ZERO))
        .isEqualTo(BeaconBlockHeader.fromState(state).getRoot());
  }
}
