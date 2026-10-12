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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.heze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconStateHezeTest {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconStateSchemaHeze schema =
      (BeaconStateSchemaHeze) spec.getGenesisSchemaDefinitions().getBeaconStateSchema();

  @Test
  void schema_shouldLeaveGapsForRemovedFields() {
    final boolean[] expectedActiveFields = new boolean[46];
    IntStream.range(0, 46).forEach(i -> expectedActiveFields[i] = true);
    expectedActiveFields[8] = false;
    expectedActiveFields[9] = false;
    expectedActiveFields[10] = false;
    expectedActiveFields[28] = false;

    assertThat(schema.getActiveFields()).isEqualTo(expectedActiveFields);
    assertThat(schema.getFieldIndex(BeaconStateFields.ETH1_DATA)).isEqualTo(-1);
    assertThat(schema.getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES)).isEqualTo(-1);
    assertThat(schema.getFieldIndex(BeaconStateFields.ETH1_DEPOSIT_INDEX)).isEqualTo(-1);
    assertThat(schema.getFieldIndex(BeaconStateFields.DEPOSIT_REQUESTS_START_INDEX)).isEqualTo(-1);
    // Fields after the gaps keep their Gloas tree positions
    assertThat(schema.getTreePosition(schema.getFieldIndex(BeaconStateFields.VALIDATORS)))
        .isEqualTo(11);
    assertThat(
            schema.getTreePosition(
                schema.getFieldIndex(BeaconStateFields.DEPOSIT_BALANCE_TO_CONSUME)))
        .isEqualTo(29);
  }

  @Test
  void removedFieldGetters_shouldReturnEmptyValuesAndLogErrors() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    try (final LogCaptor logCaptor = LogCaptor.forClass(Eip8015RemovedFields.class)) {
      final BeaconStateHeze stateHeze = (BeaconStateHeze) state;
      assertThat(stateHeze.getEth1Data()).isEqualTo(Eip8015RemovedFields.EMPTY_ETH1_DATA);
      assertThat(stateHeze.getEth1DataVotes()).isEmpty();
      assertThat(stateHeze.getEth1DepositIndex()).isEqualTo(UInt64.ZERO);
      assertThat(stateHeze.getDepositRequestsStartIndex()).isEqualTo(UInt64.ZERO);

      assertThat(logCaptor.getErrorLogs())
          .containsExactly(
              "Accessed eth1_data, which was removed in Heze (EIP-8015)",
              "Accessed eth1_data_votes, which was removed in Heze (EIP-8015)",
              "Accessed eth1_deposit_index, which was removed in Heze (EIP-8015)",
              "Accessed deposit_requests_start_index, which was removed in Heze (EIP-8015)");
    }
  }

  @Test
  void removedFieldWrites_shouldNotChangeStateAndLogErrors() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    try (final LogCaptor logCaptor = LogCaptor.forClass(Eip8015RemovedFields.class)) {
      final BeaconState updatedState =
          state.updated(
              mutableState -> {
                final MutableBeaconStateHeze mutableStateHeze =
                    (MutableBeaconStateHeze) mutableState;
                mutableStateHeze.setEth1Data(dataStructureUtil.randomEth1Data());
                mutableStateHeze.getEth1DataVotes().append(dataStructureUtil.randomEth1Data());
                mutableStateHeze.setEth1DataVotes(Eip8015RemovedFields.EMPTY_ETH1_DATA_VOTES);
                mutableStateHeze.setEth1DepositIndex(UInt64.valueOf(5));
                mutableStateHeze.setDepositRequestsStartIndex(UInt64.valueOf(5));
              });

      assertThat(updatedState.hashTreeRoot()).isEqualTo(state.hashTreeRoot());
      assertThat(logCaptor.getErrorLogs()).hasSize(5);
    }
  }

  @Test
  void eth1DataVotesSchema_shouldReturnEmptyListSchemaAndLogError() {
    try (final LogCaptor logCaptor = LogCaptor.forClass(Eip8015RemovedFields.class)) {
      assertThat(schema.getEth1DataVotesSchema())
          .isEqualTo(Eip8015RemovedFields.EMPTY_ETH1_DATA_VOTES.getSchema());
      assertThat(logCaptor.getErrorLogs())
          .containsExactly("Accessed eth1_data_votes, which was removed in Heze (EIP-8015)");
    }
  }

  @Test
  void toString_shouldNotAccessRemovedFields() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    try (final LogCaptor logCaptor = LogCaptor.forClass(Eip8015RemovedFields.class)) {
      assertThat(state.toString()).doesNotContain("eth1_data").contains("validators");
      assertThat(state.createWritableCopy().toString()).doesNotContain("eth1_data");
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void sszRoundTrip_shouldPreserveState() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    final BeaconState deserialized = schema.sszDeserialize(state.sszSerialize());

    assertThat(deserialized).isInstanceOf(BeaconStateHeze.class);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(state.hashTreeRoot());
  }
}
