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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.RESOURCE_UNAVAILABLE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientBootstrapRequestMessage;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

class LightClientBootstrapMessageHandlerTest {
  private static final String PROTOCOL_ID =
      "/eth2/beacon_chain/req/light_client_bootstrap/1/ssz_snappy";

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final LightClientBootstrapMessageHandler handler =
      new LightClientBootstrapMessageHandler(spec, combinedChainDataClient);

  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<LightClientBootstrap> callback = mock(ResponseCallback.class);

  private SignedBlockAndState blockAndState;

  @BeforeEach
  void setUp() {
    chainBuilder.generateGenesis();
    blockAndState = chainBuilder.generateBlockAtSlot(1);
    when(peer.approveRequest()).thenReturn(true);
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(UInt64.ZERO));
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(combinedChainDataClient.getBlockByBlockRoot(blockAndState.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getBlock())));
    when(combinedChainDataClient.getStateByBlockRoot(blockAndState.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getState())));
    when(combinedChainDataClient.isFinalizedEpoch(UInt64.ONE)).thenReturn(true);
    when(combinedChainDataClient.getBlockInEffectAtSlot(spec.computeStartSlotAtEpoch(UInt64.ONE)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState.getBlock())));
  }

  @Test
  void shouldRespondWithBootstrap() {
    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockAndState.getRoot()), callback);

    final LightClientBootstrap expected =
        spec.getLightClientUtilRequired(blockAndState.getSlot())
            .createLightClientBootstrap(blockAndState.getState(), blockAndState.getBlock());
    verify(callback).respondAndCompleteSuccessfully(expected);
  }

  @Test
  void shouldRespondResourceUnavailableForUnknownBlock() {
    handler.onIncomingMessage(PROTOCOL_ID, peer, request(Bytes32.random()), callback);

    assertResourceUnavailable();
  }

  @Test
  void shouldRespondResourceUnavailableWhenStateMissing() {
    when(combinedChainDataClient.getStateByBlockRoot(blockAndState.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockAndState.getRoot()), callback);

    assertResourceUnavailable();
  }

  @Test
  void shouldRespondResourceUnavailableForBlockOlderThanServableRange() {
    final UInt64 currentEpoch =
        UInt64.valueOf(spec.getNetworkingConfig().getMinEpochsForBlockRequests()).plus(10);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockAndState.getRoot()), callback);

    assertResourceUnavailable();
  }

  @Test
  void shouldRespondResourceUnavailableWhenEpochBoundaryNotFinalized() {
    when(combinedChainDataClient.isFinalizedEpoch(UInt64.ONE)).thenReturn(false);

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockAndState.getRoot()), callback);

    assertResourceUnavailable();
    verify(combinedChainDataClient, never()).getStateByBlockRoot(any());
  }

  @Test
  void shouldRespondResourceUnavailableWhenBlockIsNotEpochBoundary() {
    final SignedBlockAndState laterBlock = chainBuilder.generateBlockAtSlot(2);
    when(combinedChainDataClient.getBlockInEffectAtSlot(spec.computeStartSlotAtEpoch(UInt64.ONE)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(laterBlock.getBlock())));

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockAndState.getRoot()), callback);

    assertResourceUnavailable();
    verify(combinedChainDataClient, never()).getStateByBlockRoot(any());
  }

  @Test
  void shouldIgnoreRateLimitedRequest() {
    when(peer.approveRequest()).thenReturn(false);

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockAndState.getRoot()), callback);

    verifyNoInteractions(callback);
  }

  private void assertResourceUnavailable() {
    final ArgumentCaptor<RpcException> error = ArgumentCaptor.forClass(RpcException.class);
    verify(callback).completeWithErrorResponse(error.capture());
    assertThat(error.getValue().getResponseCode()).isEqualTo(RESOURCE_UNAVAILABLE);
  }

  private static LightClientBootstrapRequestMessage request(final Bytes32 blockRoot) {
    return new LightClientBootstrapRequestMessage(blockRoot);
  }
}
