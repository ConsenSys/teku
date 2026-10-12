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

package tech.pegasys.teku.spec.logic.versions.heze.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionRequestsProcessorHezeTest {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void processDepositRequests_shouldAppendPendingDepositWithoutStartIndex() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final DepositRequest depositRequest = dataStructureUtil.randomDepositRequest();
    final ExecutionRequestsProcessor processor =
        spec.getExecutionRequestsProcessor(state.getSlot());
    final int pendingDepositsBefore =
        BeaconStateElectra.required(state).getPendingDeposits().size();

    try (final LogCaptor logCaptor = LogCaptor.forClass(Eip8015RemovedFields.class)) {
      final BeaconState updatedState =
          state.updated(
              mutableState ->
                  processor.processDepositRequests(mutableState, List.of(depositRequest)));

      final List<PendingDeposit> pendingDeposits =
          BeaconStateElectra.required(updatedState).getPendingDeposits().asList();
      assertThat(pendingDeposits).hasSize(pendingDepositsBefore + 1);
      final PendingDeposit appended = pendingDeposits.getLast();
      assertThat(appended.getPublicKey()).isEqualTo(depositRequest.getPubkey());
      assertThat(appended.getAmount()).isEqualTo(depositRequest.getAmount());
      assertThat(appended.getSlot()).isEqualTo(state.getSlot());
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }
}
