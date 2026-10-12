/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.heze.forktransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.heze.ExecutionPayloadBidHeze;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.heze.BeaconStateHeze;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class HezeStateUpgradeTest {

  private static final UInt64 HEZE_FORK_EPOCH = UInt64.valueOf(2);

  private final Spec spec =
      TestSpecFactory.createMinimalWithCapellaDenebElectraFuluGloasAndHezeForkEpoch(
          ZERO, ZERO, ZERO, ZERO, ZERO, HEZE_FORK_EPOCH);
  private final SpecVersion hezeSpecVersion = spec.atEpoch(HEZE_FORK_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldConvertLatestExecutionPayloadBidToHezeSchema() {
    final BeaconStateGloas preState = createPreState(UInt64.valueOf(64), UInt64.valueOf(64));
    final ExecutionPayloadBid preStateBid = preState.getLatestExecutionPayloadBid();

    final BeaconStateGloas postState = getHezeStateUpgrade().upgrade(preState);
    final ExecutionPayloadBid postStateBid = postState.getLatestExecutionPayloadBid();

    assertThat(postStateBid).isInstanceOf(ExecutionPayloadBidHeze.class);
    assertThat(postStateBid.getParentBlockHash()).isEqualTo(preStateBid.getParentBlockHash());
    assertThat(postStateBid.getParentBlockRoot()).isEqualTo(preStateBid.getParentBlockRoot());
    assertThat(postStateBid.getBlockHash()).isEqualTo(preStateBid.getBlockHash());
    assertThat(postStateBid.getPrevRandao()).isEqualTo(preStateBid.getPrevRandao());
    assertThat(postStateBid.getFeeRecipient()).isEqualTo(preStateBid.getFeeRecipient());
    assertThat(postStateBid.getGasLimit()).isEqualTo(preStateBid.getGasLimit());
    assertThat(postStateBid.getBuilderIndex()).isEqualTo(preStateBid.getBuilderIndex());
    assertThat(postStateBid.getSlot()).isEqualTo(preStateBid.getSlot());
    assertThat(postStateBid.getValue()).isEqualTo(preStateBid.getValue());
    assertThat(postStateBid.getExecutionPayment()).isEqualTo(preStateBid.getExecutionPayment());
    assertThat(postStateBid.getBlobKzgCommitments()).isEqualTo(preStateBid.getBlobKzgCommitments());
    assertThat(postStateBid.getExecutionRequestsRoot())
        .isEqualTo(preStateBid.getExecutionRequestsRoot());
    assertThat(((ExecutionPayloadBidHeze) postStateBid).getInclusionListBits())
        .hasSize(
            SpecConfigHeze.required(hezeSpecVersion.getConfig()).getInclusionListCommitteeSize());
    assertThat(((ExecutionPayloadBidHeze) postStateBid).getInclusionListBits().streamAllSetBits())
        .isEmpty();
  }

  @Test
  void shouldFailWhenEth1DepositBridgeIsNotFinished() {
    final BeaconStateGloas preState =
        createPreState(UInt64.valueOf(10), SpecConfigHeze.UNSET_DEPOSIT_REQUESTS_START_INDEX);

    assertThatThrownBy(() -> getHezeStateUpgrade().upgrade(preState))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Eth1 deposit bridge");
  }

  @Test
  void shouldDropRemovedFieldsWithoutAccessingThemOnTheHezeState() {
    final BeaconStateGloas preState = createPreState(UInt64.valueOf(64), UInt64.valueOf(64));

    try (final LogCaptor logCaptor = LogCaptor.forClass(Eip8015RemovedFields.class)) {
      final BeaconStateGloas postState = getHezeStateUpgrade().upgrade(preState);

      assertThat(postState).isInstanceOf(BeaconStateHeze.class);
      assertThat(postState.getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA)).isEqualTo(-1);
      assertThat(postState.getValidators()).isEqualTo(preState.getValidators());
      assertThat(postState.getPendingDeposits()).isEqualTo(preState.getPendingDeposits());
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  private BeaconStateGloas createPreState(
      final UInt64 eth1DepositIndex, final UInt64 depositRequestsStartIndex) {
    final UInt64 hezeForkSlot = spec.computeStartSlotAtEpoch(HEZE_FORK_EPOCH);
    return BeaconStateGloas.required(
        dataStructureUtil
            .stateBuilderGloas(64, 0, 0)
            .slot(hezeForkSlot)
            .eth1DepositIndex(eth1DepositIndex)
            .depositRequestsStartIndex(depositRequestsStartIndex)
            .build());
  }

  @SuppressWarnings("unchecked")
  private StateUpgrade<BeaconStateGloas> getHezeStateUpgrade() {
    return (StateUpgrade<BeaconStateGloas>) hezeSpecVersion.getStateUpgrade().orElseThrow();
  }
}
