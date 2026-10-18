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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class HezeExecutionClientHandlerTest extends ExecutionHandlerClientTest {

  @BeforeEach
  void setup() {
    spec = TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.ONE);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void engineGetInclusionList_shouldCallGetInclusionListV1() {
    final ExecutionClientHandler handler = getHandler();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final List<Transaction> transactions =
        dataStructureUtil.randomInclusionList(2).getTransactions().asList();
    final List<String> responseData =
        transactions.stream().map(transaction -> transaction.getBytes().toHexString()).toList();
    when(executionEngineClient.getInclusionListV1(parentHash))
        .thenReturn(SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(responseData)));

    final SafeFuture<List<Transaction>> future = handler.engineGetInclusionList(parentHash, slot);

    verify(executionEngineClient).getInclusionListV1(parentHash);
    assertThat(future).isCompletedWithValue(transactions);
  }

  @Test
  void engineGetInclusionList_shouldFailForPreHezeSlotWithoutCallingExecutionClient() {
    final ExecutionClientHandler handler = getHandler();
    final UInt64 preHezeSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).minus(1);

    final SafeFuture<List<Transaction>> future =
        handler.engineGetInclusionList(dataStructureUtil.randomBytes32(), preHezeSlot);

    assertThatSafeFuture(future).isCompletedExceptionallyWith(IllegalArgumentException.class);
    verifyNoInteractions(executionEngineClient);
  }
}
