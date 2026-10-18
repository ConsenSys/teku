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

package tech.pegasys.teku.ethereum.executionclient.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.InvalidRemoteResponseException;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineGetInclusionListV1Test {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private final EngineGetInclusionListV1 jsonRpcMethod =
      new EngineGetInclusionListV1(executionEngineClient, spec);

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_getInclusionList");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(1);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_getInclusionListV1");
  }

  @Test
  public void parentHashParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void slotParamIsRequired() {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(dataStructureUtil.randomBytes32()).build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 1");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final String errorResponseFromClient = "error!";
    when(executionEngineClient.getInclusionListV1(any()))
        .thenReturn(SafeFuture.completedFuture(Response.fromErrorMessage(errorResponseFromClient)));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(dataStructureUtil.randomBytes32())
            .add(UInt64.ONE)
            .build();

    assertThat(jsonRpcMethod.execute(params))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(InvalidRemoteResponseException.class)
        .withMessageContaining(
            "Invalid remote response from the execution client: %s", errorResponseFromClient);
  }

  @Test
  public void shouldCallGetInclusionListV1AndParseResponseSuccessfully() {
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final UInt64 slot = UInt64.ONE;
    final List<Transaction> transactions =
        dataStructureUtil.randomInclusionList(3).getTransactions().asList();
    final List<String> encodedTransactions =
        transactions.stream().map(transaction -> transaction.getBytes().toHexString()).toList();
    when(executionEngineClient.getInclusionListV1(parentHash))
        .thenReturn(
            SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(encodedTransactions)));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(parentHash).add(slot).build();

    assertThat(jsonRpcMethod.execute(params)).isCompletedWithValue(transactions);

    verify(executionEngineClient).getInclusionListV1(parentHash);
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldUseSchemaAtRequestSlotWhenHezeIsNotActiveAtGenesis() {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.ONE);
    final EngineGetInclusionListV1 transitionMethod =
        new EngineGetInclusionListV1(executionEngineClient, transitionSpec);
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final UInt64 firstHezeSlot = transitionSpec.computeStartSlotAtEpoch(UInt64.ONE);
    final List<Transaction> transactions =
        new DataStructureUtil(transitionSpec).randomInclusionList(2).getTransactions().asList();
    final List<String> encodedTransactions =
        transactions.stream().map(transaction -> transaction.getBytes().toHexString()).toList();
    when(executionEngineClient.getInclusionListV1(parentHash))
        .thenReturn(
            SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(encodedTransactions)));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(parentHash).add(firstHezeSlot).build();

    assertThat(transitionMethod.execute(params)).isCompletedWithValue(transactions);
  }

  @Test
  public void shouldRejectPreHezeSlotWithoutCallingExecutionClient() {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.ONE);
    final EngineGetInclusionListV1 transitionMethod =
        new EngineGetInclusionListV1(executionEngineClient, transitionSpec);
    final UInt64 preHezeSlot = transitionSpec.computeStartSlotAtEpoch(UInt64.ONE).minus(1);

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(dataStructureUtil.randomBytes32())
            .add(preHezeSlot)
            .build();

    assertThatThrownBy(() -> transitionMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(executionEngineClient);
  }
}
