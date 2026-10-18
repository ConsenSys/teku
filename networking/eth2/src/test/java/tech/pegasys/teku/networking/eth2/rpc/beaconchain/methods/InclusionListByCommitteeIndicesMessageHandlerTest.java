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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.RESOURCE_UNAVAILABLE;

import com.google.common.base.Throwables;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.InclusionListByCommitteeRequestMessage;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.inclusionlist.InclusionListManager;

class InclusionListByCommitteeIndicesMessageHandlerTest {

  private static final UInt64 SLOT = UInt64.ONE;
  private static final RequestKey REQUEST_KEY = new RequestKey(ZERO, 42);

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigHeze specConfig = SpecConfigHeze.required(spec.getGenesisSpecConfig());
  private final String protocolId =
      BeaconChainMethodIds.getInclusionListsByIndicesMethodId(
          1, RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize()));
  private final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final InclusionListManager inclusionListManager = mock(InclusionListManager.class);
  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<SignedInclusionList> callback = mock(ResponseCallback.class);

  private final InclusionListByCommitteeIndicesMessageHandler handler =
      new InclusionListByCommitteeIndicesMessageHandler(spec, metricsSystem, inclusionListManager);

  @BeforeEach
  void setUp() {
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveInclusionListsRequest(any(), anyLong())).thenReturn(Optional.of(REQUEST_KEY));
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  void shouldRespondWithAllInclusionListsFromManager() {
    final InclusionListByCommitteeRequestMessage request = request(0, 3);
    final List<SignedInclusionList> inclusionLists =
        List.of(signedInclusionList(UInt64.valueOf(5)), signedInclusionList(UInt64.valueOf(9)));
    when(inclusionListManager.getInclusionLists(SLOT, dependentRoot, request.getCommitteeIndices()))
        .thenReturn(SafeFuture.completedFuture(inclusionLists));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    verify(peer).approveInclusionListsRequest(callback, 2);
    verify(callback).respond(inclusionLists.get(0));
    verify(callback).respond(inclusionLists.get(1));
    verify(callback).completeSuccessfully();
    // Every requested inclusion list was sent: no rate limiter adjustment required
    verify(peer, never()).adjustInclusionListsRequest(any(), anyLong());
    assertThat(getRequestCounterValue("ok")).isOne();
    assertThat(getRequestedCounterValue()).isEqualTo(2);
  }

  @Test
  void shouldWaitForManagerAndAdjustRateLimiterWhenFewerListsAreAvailable() {
    final InclusionListByCommitteeRequestMessage request = request(0, 3);
    final SignedInclusionList inclusionList = signedInclusionList(UInt64.valueOf(5));
    final SafeFuture<List<SignedInclusionList>> lookup = new SafeFuture<>();
    when(inclusionListManager.getInclusionLists(SLOT, dependentRoot, request.getCommitteeIndices()))
        .thenReturn(lookup);

    handler.onIncomingMessage(protocolId, peer, request, callback);
    verify(callback, never()).respond(any());
    verify(callback, never()).completeSuccessfully();

    lookup.complete(List.of(inclusionList));

    verify(callback).respond(inclusionList);
    verify(callback).completeSuccessfully();
    verify(peer).adjustInclusionListsRequest(REQUEST_KEY, 1);
  }

  @Test
  void shouldCompleteWithUnexpectedErrorWhenLookupFails() {
    final InclusionListByCommitteeRequestMessage request = request(0);
    final RuntimeException error = new RuntimeException("lookup failed");
    when(inclusionListManager.getInclusionLists(SLOT, dependentRoot, request.getCommitteeIndices()))
        .thenReturn(SafeFuture.failedFuture(error));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    verify(callback, never()).respond(any());
    verify(callback, never()).completeSuccessfully();
    final ArgumentCaptor<Throwable> reportedError = ArgumentCaptor.forClass(Throwable.class);
    verify(callback).completeWithUnexpectedError(reportedError.capture());
    assertThat(Throwables.getRootCause(reportedError.getValue())).isSameAs(error);
    verify(peer).adjustInclusionListsRequest(REQUEST_KEY, 0);
  }

  @Test
  void shouldNotRespondWhenPeerIsRateLimited() {
    when(peer.approveRequest()).thenReturn(false);

    handler.onIncomingMessage(protocolId, peer, request(0, 1), callback);

    verify(inclusionListManager, never()).getInclusionLists(any(), any(), any());
    verify(callback, never()).respond(any());
    verify(callback, never()).completeSuccessfully();
    assertThat(getRequestCounterValue("rate_limited")).isOne();
    assertThat(getRequestCounterValue("ok")).isZero();
  }

  @Test
  void shouldNotRespondWhenInclusionListRequestIsNotApproved() {
    when(peer.approveInclusionListsRequest(eq(callback), eq(2L))).thenReturn(Optional.empty());

    handler.onIncomingMessage(protocolId, peer, request(0, 1), callback);

    verify(inclusionListManager, never()).getInclusionLists(any(), any(), any());
    verify(callback, never()).respond(any());
    assertThat(getRequestCounterValue("rate_limited")).isOne();
  }

  @Test
  void validateRequest_shouldAcceptRequestUpToMaxRequestInclusionList() {
    final int[] allPositions =
        IntStream.range(0, specConfig.getInclusionListCommitteeSize()).toArray();

    assertThat(handler.validateRequest(protocolId, request(allPositions))).isEmpty();
  }

  @Test
  void validateRequest_shouldRejectRequestForPreHezeSlot() {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.ONE);
    final InclusionListByCommitteeIndicesMessageHandler transitionHandler =
        new InclusionListByCommitteeIndicesMessageHandler(
            transitionSpec, metricsSystem, inclusionListManager);
    final UInt64 lastPreHezeSlot =
        transitionSpec.computeStartSlotAtEpoch(UInt64.ONE).minusMinZero(1);

    final Optional<RpcException> result =
        transitionHandler.validateRequest(protocolId, request(lastPreHezeSlot, 0));

    assertThat(result)
        .hasValueSatisfying(
            error -> assertThat(error.getResponseCode()).isEqualTo(RESOURCE_UNAVAILABLE));
    assertThat(getRequestCounterValue("pre_heze_slot")).isOne();
  }

  @Test
  void validateRequest_shouldAcceptRequestForFirstHezeSlot() {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.ONE);
    final InclusionListByCommitteeIndicesMessageHandler transitionHandler =
        new InclusionListByCommitteeIndicesMessageHandler(
            transitionSpec, metricsSystem, inclusionListManager);
    final UInt64 firstHezeSlot = transitionSpec.computeStartSlotAtEpoch(UInt64.ONE);

    assertThat(transitionHandler.validateRequest(protocolId, request(firstHezeSlot, 0))).isEmpty();
  }

  private InclusionListByCommitteeRequestMessage request(final int... positions) {
    return request(SLOT, positions);
  }

  private InclusionListByCommitteeRequestMessage request(
      final UInt64 slot, final int... positions) {
    final SszBitvector committeeIndices =
        SszBitvectorSchema.create(specConfig.getInclusionListCommitteeSize()).ofBits(positions);
    return new InclusionListByCommitteeRequestMessage(
        slot, dependentRoot, committeeIndices, specConfig);
  }

  private SignedInclusionList signedInclusionList(final UInt64 validatorIndex) {
    final SchemaDefinitionsHeze schemaDefinitions =
        SchemaDefinitionsHeze.required(spec.getGenesisSchemaDefinitions());
    return schemaDefinitions
        .getSignedInclusionListSchema()
        .create(
            schemaDefinitions
                .getInclusionListSchema()
                .create(SLOT, validatorIndex, dependentRoot, List.of()),
            dataStructureUtil.randomSignature());
  }

  private long getRequestCounterValue(final String label) {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.NETWORK,
        "rpc_inclusion_list_by_committee_indices_requests_total",
        label);
  }

  private long getRequestedCounterValue() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.NETWORK, "rpc_inclusion_list_by_committee_indices_requested_total");
  }
}
