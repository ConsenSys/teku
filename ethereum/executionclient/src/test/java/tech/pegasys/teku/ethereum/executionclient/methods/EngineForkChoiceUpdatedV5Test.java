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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.InvalidRemoteResponseException;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResultV2;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV5;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV2;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.WithdrawalV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineForkChoiceUpdatedV5Test {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private final EngineForkChoiceUpdatedV5 jsonRpcMethod =
      new EngineForkChoiceUpdatedV5(executionEngineClient);

  private final ForkChoiceState forkChoiceState = dataStructureUtil.randomForkChoiceState(false);
  private final ForkChoiceStateV1 forkChoiceStateV1 =
      ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState);

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_forkchoiceUpdated");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(5);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_forkchoiceUpdatedV5");
  }

  @Test
  public void forkChoiceStateParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index 0");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void payloadBuildingAttributesParamIsOptional() {
    when(executionEngineClient.forkChoiceUpdatedV5(any(), eq(Optional.empty())))
        .thenReturn(successfulResponse(ExecutionPayloadStatus.VALID, null, null));

    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(forkChoiceState).build();

    assertThat(jsonRpcMethod.execute(params)).isCompleted();

    verify(executionEngineClient).forkChoiceUpdatedV5(forkChoiceStateV1, Optional.empty());
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldCallForkChoiceUpdatedV5WithPayloadAttributesV5() {
    final PayloadBuildingAttributes payloadBuildingAttributes =
        dataStructureUtil.randomPayloadBuildingAttributes(false);
    final PayloadAttributesV5 expectedPayloadAttributesV5 =
        PayloadAttributesV5.fromInternalPayloadBuildingAttributesV5(payloadBuildingAttributes);

    when(executionEngineClient.forkChoiceUpdatedV5(
            forkChoiceStateV1, Optional.of(expectedPayloadAttributesV5)))
        .thenReturn(successfulResponse(ExecutionPayloadStatus.VALID, null, null));

    assertThat(jsonRpcMethod.execute(paramsWithAttributes(payloadBuildingAttributes)))
        .isCompleted();

    verify(executionEngineClient)
        .forkChoiceUpdatedV5(forkChoiceStateV1, Optional.of(expectedPayloadAttributesV5));
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldMapEveryPayloadBuildingAttributeToPayloadAttributesV5() {
    final PayloadBuildingAttributes payloadBuildingAttributes =
        dataStructureUtil.randomPayloadBuildingAttributes(false);
    // Heze attributes must carry a non-empty inclusion list for this test to be meaningful
    assertThat(payloadBuildingAttributes.inclusionListTransactions()).isNotEmpty();

    when(executionEngineClient.forkChoiceUpdatedV5(any(), any()))
        .thenReturn(successfulResponse(ExecutionPayloadStatus.VALID, null, null));

    assertThat(jsonRpcMethod.execute(paramsWithAttributes(payloadBuildingAttributes)))
        .isCompleted();

    final PayloadAttributesV5 payloadAttributes = captureSentPayloadAttributes();

    assertThat(payloadAttributes.timestamp).isEqualTo(payloadBuildingAttributes.timestamp());
    assertThat(payloadAttributes.prevRandao).isEqualTo(payloadBuildingAttributes.prevRandao());
    assertThat(payloadAttributes.suggestedFeeRecipient)
        .isEqualTo(payloadBuildingAttributes.feeRecipient());
    assertThat(payloadAttributes.parentBeaconBlockRoot)
        .isEqualTo(payloadBuildingAttributes.parentBeaconBlock().blockRoot());
    assertThat(payloadAttributes.slotNumber).isEqualTo(payloadBuildingAttributes.proposalSlot());
    assertThat(payloadAttributes.targetGasLimit)
        .isEqualTo(payloadBuildingAttributes.targetGasLimit());

    final List<Withdrawal> expectedWithdrawals =
        payloadBuildingAttributes.withdrawals().orElseThrow();
    assertThat(payloadAttributes.withdrawals)
        .containsExactlyElementsOf(
            expectedWithdrawals.stream()
                .map(
                    withdrawal ->
                        new WithdrawalV1(
                            withdrawal.getIndex(),
                            withdrawal.getValidatorIndex(),
                            withdrawal.getAddress(),
                            withdrawal.getAmount()))
                .toList());

    assertThat(payloadAttributes.inclusionListTransactions)
        .containsExactlyElementsOf(
            payloadBuildingAttributes.inclusionListTransactions().stream()
                .map(Bytes::toHexString)
                .toList());
  }

  @Test
  public void shouldSendEmptyInclusionListWhenAttributesHaveNoInclusionListTransactions() {
    // Pre-Heze payload building attributes carry no inclusion list transactions
    final DataStructureUtil gloasDataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalGloas());
    final PayloadBuildingAttributes payloadBuildingAttributes =
        gloasDataStructureUtil.randomPayloadBuildingAttributes(false);
    assertThat(payloadBuildingAttributes.inclusionListTransactions()).isEmpty();

    when(executionEngineClient.forkChoiceUpdatedV5(any(), any()))
        .thenReturn(successfulResponse(ExecutionPayloadStatus.VALID, null, null));

    assertThat(jsonRpcMethod.execute(paramsWithAttributes(payloadBuildingAttributes)))
        .isCompleted();

    final PayloadAttributesV5 payloadAttributes = captureSentPayloadAttributes();
    assertThat(payloadAttributes.inclusionListTransactions).isNotNull().isEmpty();
  }

  @Test
  public void shouldFailWhenPayloadBuildingAttributesHaveNoWithdrawals() {
    final PayloadBuildingAttributes withWithdrawals =
        dataStructureUtil.randomPayloadBuildingAttributes(false);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        new PayloadBuildingAttributes(
            withWithdrawals.proposerIndex(),
            withWithdrawals.proposalSlot(),
            withWithdrawals.timestamp(),
            withWithdrawals.prevRandao(),
            withWithdrawals.feeRecipient(),
            withWithdrawals.targetGasLimit(),
            withWithdrawals.validatorRegistration(),
            Optional.empty(),
            withWithdrawals.parentBeaconBlock(),
            withWithdrawals.inclusionListTransactions());

    assertThatThrownBy(() -> jsonRpcMethod.execute(paramsWithAttributes(payloadBuildingAttributes)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Withdrawals were expected to be part of the payload attributes");

    verifyNoInteractions(executionEngineClient);
  }

  @Test
  public void shouldMapInclusionListNotSatisfiedToForkChoiceUpdatedResult() {
    final Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    when(executionEngineClient.forkChoiceUpdatedV5(any(), any()))
        .thenReturn(
            successfulResponse(
                new PayloadStatusV2(ExecutionPayloadStatus.VALID, latestValidHash, null, false),
                payloadId));

    final ForkChoiceUpdatedResult result = executeSuccessfully(forkChoiceStateOnlyParams());

    assertThat(result.getPayloadId()).contains(payloadId);
    final PayloadStatus payloadStatus = result.getPayloadStatus();
    assertThat(payloadStatus.hasValidStatus()).isTrue();
    assertThat(payloadStatus.getLatestValidHash()).contains(latestValidHash);
    assertThat(payloadStatus.getInclusionListSatisfied()).contains(false);
  }

  @Test
  public void shouldMapInclusionListSatisfiedToForkChoiceUpdatedResult() {
    when(executionEngineClient.forkChoiceUpdatedV5(any(), any()))
        .thenReturn(successfulResponse(ExecutionPayloadStatus.VALID, true, null));

    final ForkChoiceUpdatedResult result = executeSuccessfully(forkChoiceStateOnlyParams());

    assertThat(result.getPayloadId()).isEmpty();
    assertThat(result.getPayloadStatus().getInclusionListSatisfied()).contains(true);
  }

  @Test
  public void shouldMapAbsentInclusionListSatisfiedToEmpty() {
    when(executionEngineClient.forkChoiceUpdatedV5(any(), any()))
        .thenReturn(successfulResponse(ExecutionPayloadStatus.SYNCING, null, null));

    final ForkChoiceUpdatedResult result = executeSuccessfully(forkChoiceStateOnlyParams());

    assertThat(result.getPayloadStatus().hasStatus(ExecutionPayloadStatus.SYNCING)).isTrue();
    assertThat(result.getPayloadStatus().getInclusionListSatisfied()).isEmpty();
  }

  @Test
  public void shouldMapInvalidStatusWithValidationError() {
    final Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    when(executionEngineClient.forkChoiceUpdatedV5(any(), any()))
        .thenReturn(
            successfulResponse(
                new PayloadStatusV2(
                    ExecutionPayloadStatus.INVALID, latestValidHash, "invalid head", null),
                null));

    final ForkChoiceUpdatedResult result = executeSuccessfully(forkChoiceStateOnlyParams());

    assertThat(result.getPayloadStatus())
        .isEqualTo(
            PayloadStatus.invalid(Optional.of(latestValidHash), Optional.of("invalid head")));
    assertThat(result.getPayloadId()).isEmpty();
  }

  @Test
  public void shouldReturnFailedFutureWithMessageWhenEngineClientRequestFails() {
    final String errorResponseFromClient = "error!";
    when(executionEngineClient.forkChoiceUpdatedV5(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Response.fromErrorMessage(errorResponseFromClient)));

    assertThat(jsonRpcMethod.execute(forkChoiceStateOnlyParams()))
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(InvalidRemoteResponseException.class)
        .withMessageContaining(
            "Invalid remote response from the execution client: %s", errorResponseFromClient);
  }

  private PayloadAttributesV5 captureSentPayloadAttributes() {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Optional<PayloadAttributesV5>> captor =
        ArgumentCaptor.forClass(Optional.class);
    verify(executionEngineClient).forkChoiceUpdatedV5(eq(forkChoiceStateV1), captor.capture());
    return captor.getValue().orElseThrow();
  }

  private ForkChoiceUpdatedResult executeSuccessfully(final JsonRpcRequestParams params) {
    final SafeFuture<ForkChoiceUpdatedResult> result = jsonRpcMethod.execute(params);
    assertThat(result).isCompleted();
    return result.getImmediately();
  }

  private JsonRpcRequestParams forkChoiceStateOnlyParams() {
    return new JsonRpcRequestParams.Builder().add(forkChoiceState).build();
  }

  private JsonRpcRequestParams paramsWithAttributes(
      final PayloadBuildingAttributes payloadBuildingAttributes) {
    return new JsonRpcRequestParams.Builder()
        .add(forkChoiceState)
        .add(payloadBuildingAttributes)
        .build();
  }

  private SafeFuture<Response<ForkChoiceUpdatedResultV2>> successfulResponse(
      final ExecutionPayloadStatus status,
      final Boolean inclusionListSatisfied,
      final Bytes8 payloadId) {
    return successfulResponse(
        new PayloadStatusV2(status, null, null, inclusionListSatisfied), payloadId);
  }

  private static SafeFuture<Response<ForkChoiceUpdatedResultV2>> successfulResponse(
      final PayloadStatusV2 payloadStatus, final Bytes8 payloadId) {
    return SafeFuture.completedFuture(
        Response.fromPayloadReceivedAsJson(
            new ForkChoiceUpdatedResultV2(payloadStatus, payloadId)));
  }
}
