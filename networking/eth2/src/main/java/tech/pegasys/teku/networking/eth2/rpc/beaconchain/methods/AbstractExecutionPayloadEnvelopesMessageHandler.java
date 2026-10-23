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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * Base handler for execution payload envelope requests. Envelopes may need unblinding via the EL,
 * so requests are capped, charged for work done, served in batches and stopped on stream closure.
 */
public abstract class AbstractExecutionPayloadEnvelopesMessageHandler<
        TRequest, TRequestState extends ExecutionPayloadEnvelopesRequestState>
    extends PeerRequiredLocalMessageHandler<TRequest, SignedExecutionPayloadEnvelope> {

  private static final Logger LOG = LogManager.getLogger();

  /** Max envelopes unblinded per EL call. */
  static final int DEFAULT_UNBLINDING_BATCH_SIZE = 32;

  protected final Spec spec;
  protected final CombinedChainDataClient combinedChainDataClient;
  protected final LabelledMetric<Counter> requestCounter;
  protected final int unblindingBatchSize;
  private final Counter totalExecutionPayloadEnvelopesRequestedCounter;
  private final ExecutionPayloadEnvelopesRequestLimiter requestLimiter;
  private final String requestType;

  /**
   * @param methodName used in metric names, e.g. {@code by_range}
   */
  protected AbstractExecutionPayloadEnvelopesMessageHandler(
      final Spec spec,
      final CombinedChainDataClient combinedChainDataClient,
      final MetricsSystem metricsSystem,
      final ExecutionPayloadEnvelopesRequestLimiter requestLimiter,
      final int unblindingBatchSize,
      final String methodName) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    this.requestLimiter = requestLimiter;
    this.unblindingBatchSize = unblindingBatchSize;
    final String methodDescription = methodName.replace('_', ' ');
    this.requestType = "execution payload envelopes " + methodDescription;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_" + methodName + "_requests_total",
            "Total number of execution payload envelopes "
                + methodDescription
                + " requests received",
            "status");
    totalExecutionPayloadEnvelopesRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_execution_payload_envelopes_" + methodName + "_requested_envelopes_total",
            "Total number of execution payload envelopes requested in accepted execution payload envelopes "
                + methodDescription
                + " requests from peers");
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final TRequest message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback) {
    final long requestedCount = getRequestedCount(message);
    LOG.trace(
        "Peer {} requested {} execution payload envelopes: {}",
        peer.getId(),
        requestedCount,
        message);

    final Optional<RequestKey> maybeRequestKey =
        peer.approveExecutionPayloadEnvelopesRequest(callback, requestedCount);

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    final TRequestState requestState = createRequestState(message, callback);

    // Cap concurrent requests that may hit the EL. Rejected requests are not refunded.
    final boolean needsUnblinding = requestState.needsUnblinding();
    if (needsUnblinding && !requestLimiter.tryAcquire(peer.getId())) {
      requestCounter.labels("concurrency_limited").inc();
      callback.completeWithErrorResponse(
          new ResourceUnavailableException(
              "Too many execution payload envelope requests are being served"));
      return;
    }
    requestCounter.labels("ok").inc();
    totalExecutionPayloadEnvelopesRequestedCounter.inc(requestedCount);

    SafeFuture<Void> response = serve(requestState);
    if (needsUnblinding) {
      response = response.alwaysRun(() -> requestLimiter.release(peer.getId()));
    }

    response.finish(
        () -> {
          // Refund only the work never done, not envelopes never sent
          if (requestState.getChargedCount() != requestedCount) {
            peer.adjustExecutionPayloadEnvelopesRequest(
                maybeRequestKey.get(), requestState.getChargedCount());
          }
          callback.completeSuccessfully();
        },
        err -> handleError(err, callback, requestType));
  }

  protected abstract long getRequestedCount(TRequest message);

  protected abstract TRequestState createRequestState(
      TRequest message, ResponseCallback<SignedExecutionPayloadEnvelope> callback);

  /** Sends the envelopes and charges the state; fails if the stream is closed mid-request. */
  protected abstract SafeFuture<Void> serve(TRequestState requestState);

  protected UInt64 getMinServableEpoch() {
    final UInt64 currentEpoch =
        combinedChainDataClient.getRecentChainData().getCurrentEpoch().orElse(ZERO);
    return currentEpoch
        .minusMinZero(spec.getNetworkingConfig().getMinEpochsForBlockRequests())
        .max(spec.getSpecConfig(currentEpoch).getGloasForkEpoch());
  }

  /** Loads a batch of envelopes in one lookup, keeping batch order and dropping rejected ones. */
  protected <T> SafeFuture<List<SignedExecutionPayloadEnvelope>> loadExecutionPayloadEnvelopes(
      final List<T> batch,
      final Function<T, Bytes32> blockRootOf,
      final BiPredicate<T, SignedExecutionPayloadEnvelope> accepts) {
    if (batch.isEmpty()) {
      return completedFuture(List.of());
    }
    final Set<Bytes32> blockRoots = batch.stream().map(blockRootOf).collect(Collectors.toSet());
    return combinedChainDataClient
        .getExecutionPayloadsByBlockRoots(blockRoots)
        .thenApply(
            executionPayloads ->
                batch.stream()
                    .map(
                        item ->
                            Optional.ofNullable(executionPayloads.get(blockRootOf.apply(item)))
                                .filter(executionPayload -> accepts.test(item, executionPayload)))
                    .flatMap(Optional::stream)
                    .toList());
  }
}
