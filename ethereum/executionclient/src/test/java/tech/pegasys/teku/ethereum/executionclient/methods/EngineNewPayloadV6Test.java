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
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.InvalidRemoteResponseException;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV4;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV2;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EngineNewPayloadV6Test {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);
  private final EngineNewPayloadV6 jsonRpcMethod = new EngineNewPayloadV6(executionEngineClient);

  private final ExecutionPayload executionPayload =
      dataStructureUtil.randomExecutionPayload(UInt64.ONE);
  private final List<VersionedHash> blobVersionedHashes =
      dataStructureUtil.randomVersionedHashes(3);
  private final Bytes32 parentBeaconBlockRoot = dataStructureUtil.randomBytes32();
  private final List<Bytes> executionRequests = dataStructureUtil.randomEncodedExecutionRequests();
  private final List<Bytes> inclusionListTransactions =
      List.of(
          dataStructureUtil.randomBytes(32),
          dataStructureUtil.randomBytes(64),
          dataStructureUtil.randomBytes(128));

  @Test
  public void shouldReturnExpectedNameAndVersion() {
    assertThat(jsonRpcMethod.getName()).isEqualTo("engine_newPayload");
    assertThat(jsonRpcMethod.getVersion()).isEqualTo(6);
    assertThat(jsonRpcMethod.getVersionedName()).isEqualTo("engine_newPayloadV6");
  }

  @Test
  public void executionPayloadParamIsRequired() {
    final JsonRpcRequestParams params = new JsonRpcRequestParams.Builder().build();

    assertMissingParameter(params, 0);
  }

  @Test
  public void blobVersionedHashesParamIsRequired() {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).build();

    assertMissingParameter(params, 1);
  }

  @Test
  public void parentBeaconBlockRootParamIsRequired() {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder().add(executionPayload).add(blobVersionedHashes).build();

    assertMissingParameter(params, 2);
  }

  @Test
  public void executionRequestsParamIsRequired() {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(executionPayload)
            .add(blobVersionedHashes)
            .add(parentBeaconBlockRoot)
            .build();

    assertMissingParameter(params, 3);
  }

  @Test
  public void inclusionListTransactionsParamIsRequired() {
    final JsonRpcRequestParams params =
        new JsonRpcRequestParams.Builder()
            .add(executionPayload)
            .add(blobVersionedHashes)
            .add(parentBeaconBlockRoot)
            .add(executionRequests)
            .build();

    assertMissingParameter(params, 4);
  }

  @Test
  public void shouldCallNewPayloadV6WithExecutionPayloadV4AndInclusionListTransactions() {
    final ExecutionPayloadV4 executionPayloadV4 =
        ExecutionPayloadV4.fromInternalExecutionPayload(executionPayload);
    assertThat(executionPayloadV4).isExactlyInstanceOf(ExecutionPayloadV4.class);

    when(executionEngineClient.newPayloadV6(
            eq(executionPayloadV4),
            eq(blobVersionedHashes),
            eq(parentBeaconBlockRoot),
            eq(executionRequests),
            eq(inclusionListTransactions)))
        .thenReturn(
            successfulResponse(
                new PayloadStatusV2(
                    ExecutionPayloadStatus.ACCEPTED,
                    dataStructureUtil.randomBytes32(),
                    null,
                    null)));

    assertThat(jsonRpcMethod.execute(fullParams(inclusionListTransactions))).isCompleted();

    verify(executionEngineClient)
        .newPayloadV6(
            eq(executionPayloadV4),
            eq(blobVersionedHashes),
            eq(parentBeaconBlockRoot),
            eq(executionRequests),
            eq(inclusionListTransactions));
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void shouldPassEmptyInclusionListTransactionsThrough() {
    when(executionEngineClient.newPayloadV6(any(), any(), any(), any(), any()))
        .thenReturn(
            successfulResponse(
                new PayloadStatusV2(ExecutionPayloadStatus.VALID, null, null, true)));

    assertThat(jsonRpcMethod.execute(fullParams(List.of()))).isCompleted();

    verify(executionEngineClient)
        .newPayloadV6(
            any(),
            eq(blobVersionedHashes),
            eq(parentBeaconBlockRoot),
            eq(executionRequests),
            eq(List.of()));
  }

  @Test
  public void shouldMapInclusionListNotSatisfiedToPayloadStatus() {
    final Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    when(executionEngineClient.newPayloadV6(any(), any(), any(), any(), any()))
        .thenReturn(
            successfulResponse(
                new PayloadStatusV2(ExecutionPayloadStatus.VALID, latestValidHash, null, false)));

    final PayloadStatus payloadStatus = executeSuccessfully(fullParams(inclusionListTransactions));

    assertThat(payloadStatus.hasFailedExecution()).isFalse();
    assertThat(payloadStatus.hasValidStatus()).isTrue();
    assertThat(payloadStatus.getLatestValidHash()).contains(latestValidHash);
    assertThat(payloadStatus.getValidationError()).isEmpty();
    assertThat(payloadStatus.getInclusionListSatisfied()).contains(false);
  }

  @Test
  public void shouldMapInclusionListSatisfiedToPayloadStatus() {
    final Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    when(executionEngineClient.newPayloadV6(any(), any(), any(), any(), any()))
        .thenReturn(
            successfulResponse(
                new PayloadStatusV2(ExecutionPayloadStatus.VALID, latestValidHash, null, true)));

    final PayloadStatus payloadStatus = executeSuccessfully(fullParams(inclusionListTransactions));

    assertThat(payloadStatus)
        .isEqualTo(
            PayloadStatus.valid(Optional.of(latestValidHash), Optional.empty(), Optional.of(true)));
    assertThat(payloadStatus.getInclusionListSatisfied()).contains(true);
  }

  @Test
  public void shouldMapAbsentInclusionListSatisfiedToEmpty() {
    when(executionEngineClient.newPayloadV6(any(), any(), any(), any(), any()))
        .thenReturn(
            successfulResponse(
                new PayloadStatusV2(ExecutionPayloadStatus.SYNCING, null, null, null)));

    final PayloadStatus payloadStatus = executeSuccessfully(fullParams(inclusionListTransactions));

    assertThat(payloadStatus.hasStatus(ExecutionPayloadStatus.SYNCING)).isTrue();
    assertThat(payloadStatus.getLatestValidHash()).isEmpty();
    assertThat(payloadStatus.getInclusionListSatisfied()).isEmpty();
  }

  @Test
  public void shouldMapInvalidStatusWithLatestValidHashAndValidationError() {
    final Bytes32 latestValidHash = dataStructureUtil.randomBytes32();
    when(executionEngineClient.newPayloadV6(any(), any(), any(), any(), any()))
        .thenReturn(
            successfulResponse(
                new PayloadStatusV2(
                    ExecutionPayloadStatus.INVALID, latestValidHash, "bad payload", null)));

    final PayloadStatus payloadStatus = executeSuccessfully(fullParams(inclusionListTransactions));

    assertThat(payloadStatus)
        .isEqualTo(PayloadStatus.invalid(Optional.of(latestValidHash), Optional.of("bad payload")));
    assertThat(payloadStatus.getInclusionListSatisfied()).isEmpty();
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientRequestFails() {
    final String errorResponseFromClient = "error!";
    when(executionEngineClient.newPayloadV6(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Response.fromErrorMessage(errorResponseFromClient)));

    final SafeFuture<PayloadStatus> result =
        jsonRpcMethod.execute(fullParams(inclusionListTransactions));

    assertThat(result)
        .succeedsWithin(1, TimeUnit.SECONDS)
        .matches(PayloadStatus::hasFailedExecution);
    final PayloadStatus payloadStatus = result.getImmediately();
    assertThat(payloadStatus.getStatus()).isEmpty();
    assertThat(payloadStatus.getInclusionListSatisfied()).isEmpty();
    assertThat(payloadStatus.getFailureCause())
        .hasValueSatisfying(
            cause ->
                assertThat(cause)
                    .hasRootCauseInstanceOf(InvalidRemoteResponseException.class)
                    .hasMessageContaining(errorResponseFromClient));
  }

  @Test
  public void shouldReturnFailedExecutionWhenEngineClientFutureFailsExceptionally() {
    final RuntimeException error = new RuntimeException("connection refused");
    when(executionEngineClient.newPayloadV6(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.failedFuture(error));

    final PayloadStatus payloadStatus = executeSuccessfully(fullParams(inclusionListTransactions));

    assertThat(payloadStatus.hasFailedExecution()).isTrue();
    assertThat(payloadStatus.getFailureCause())
        .hasValueSatisfying(cause -> assertThat(cause).hasRootCause(error));
  }

  private PayloadStatus executeSuccessfully(final JsonRpcRequestParams params) {
    final SafeFuture<PayloadStatus> result = jsonRpcMethod.execute(params);
    assertThat(result).isCompleted();
    return result.getImmediately();
  }

  private JsonRpcRequestParams fullParams(final List<Bytes> inclusionList) {
    return new JsonRpcRequestParams.Builder()
        .add(executionPayload)
        .add(blobVersionedHashes)
        .add(parentBeaconBlockRoot)
        .add(executionRequests)
        .add(inclusionList)
        .build();
  }

  private void assertMissingParameter(final JsonRpcRequestParams params, final int index) {
    assertThatThrownBy(() -> jsonRpcMethod.execute(params))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required parameter at index " + index);

    verifyNoInteractions(executionEngineClient);
  }

  private static SafeFuture<Response<PayloadStatusV2>> successfulResponse(
      final PayloadStatusV2 payloadStatus) {
    return SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(payloadStatus));
  }
}
