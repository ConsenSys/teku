/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.core;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;

public abstract class PeerRequiredLocalMessageHandler<I, O> implements LocalMessageHandler<I, O> {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void onIncomingMessage(
      final Optional<Eth2Peer> maybePeer, final I message, final ResponseCallback<O> callback) {
    maybePeer.ifPresentOrElse(
        peer -> onIncomingMessage(peer, message, callback),
        () -> {
          LOG.trace("Ignoring message of type {} from disconnected peer", message.getClass());
          callback.completeWithError(
              new RpcException(RpcResponseStatus.SERVER_ERROR_CODE, "Peer disconnected"));
        });
  }

  protected abstract void onIncomingMessage(
      final Eth2Peer peer, final I message, final ResponseCallback<O> callback);
}
