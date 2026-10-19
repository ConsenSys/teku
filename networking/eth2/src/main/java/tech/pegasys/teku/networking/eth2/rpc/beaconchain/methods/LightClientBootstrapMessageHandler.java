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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientBootstrapRequestMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class LightClientBootstrapMessageHandler
    extends PeerRequiredLocalMessageHandler<
        LightClientBootstrapRequestMessage, LightClientBootstrap> {

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;

  public LightClientBootstrapMessageHandler(
      final Spec spec, final CombinedChainDataClient combinedChainDataClient) {
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final LightClientBootstrapRequestMessage message,
      final ResponseCallback<LightClientBootstrap> callback) {
    if (!peer.approveRequest()) {
      return;
    }

    final Bytes32 blockRoot = message.getBlockRoot();
    combinedChainDataClient
        .getBlockByBlockRoot(blockRoot)
        .thenCompose(
            maybeBlock ->
                maybeBlock
                    .filter(this::isWithinServableRange)
                    .map(block -> createBootstrap(blockRoot, block))
                    .orElse(SafeFuture.completedFuture(Optional.empty())))
        .finish(
            maybeBootstrap ->
                maybeBootstrap.ifPresentOrElse(
                    callback::respondAndCompleteSuccessfully,
                    () ->
                        callback.completeWithErrorResponse(
                            new ResourceUnavailableException(
                                "No light client bootstrap available for block root "
                                    + blockRoot))),
            err -> handleError(err, callback, "light client bootstrap"));
  }

  private SafeFuture<Optional<LightClientBootstrap>> createBootstrap(
      final Bytes32 blockRoot, final SignedBeaconBlock block) {
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .thenApply(
            maybeState ->
                maybeState.flatMap(
                    state ->
                        spec.getLightClientUtil(block.getSlot())
                            .map(util -> util.createLightClientBootstrap(state, block))));
  }

  private boolean isWithinServableRange(final SignedBeaconBlock block) {
    final UInt64 currentEpoch =
        combinedChainDataClient.getRecentChainData().getCurrentEpoch().orElse(UInt64.ZERO);
    final UInt64 minEpoch =
        currentEpoch.minusMinZero(spec.getNetworkingConfig().getMinEpochsForBlockRequests());
    return spec.computeEpochAtSlot(block.getSlot()).isGreaterThanOrEqualTo(minEpoch);
  }
}
