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

package tech.pegasys.artemis.network.p2p.peer;

import java.util.Optional;
import javax.naming.OperationNotSupportedException;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.network.PeerAddress;
import tech.pegasys.artemis.networking.p2p.peer.DisconnectRequestHandler;
import tech.pegasys.artemis.networking.p2p.peer.DisconnectRequestHandler.DisconnectReason;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;
import tech.pegasys.artemis.networking.p2p.peer.PeerDisconnectedSubscriber;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.events.Subscribers;

public class StubPeer implements Peer {

  private final PeerAddress peerAddress;
  private Subscribers<PeerDisconnectedSubscriber> disconnectedSubscribers =
      Subscribers.create(false);
  private boolean connected = true;
  private Optional<DisconnectReason> disconnectReason = Optional.empty();

  public StubPeer() {
    this(new MockNodeId());
  }

  public StubPeer(final NodeId nodeId) {
    peerAddress = new PeerAddress(nodeId);
  }

  @Override
  public PeerAddress getAddress() {
    return peerAddress;
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  @Override
  public void disconnectImmediately() {
    disconnectedSubscribers.forEach(PeerDisconnectedSubscriber::onDisconnected);
    connected = false;
  }

  @Override
  public void disconnectCleanly(final DisconnectReason reason) {
    disconnectReason = Optional.of(reason);
    disconnectedSubscribers.forEach(PeerDisconnectedSubscriber::onDisconnected);
    connected = false;
  }

  public Optional<DisconnectReason> getDisconnectReason() {
    return disconnectReason;
  }

  @Override
  public void setDisconnectRequestHandler(final DisconnectRequestHandler handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void subscribeDisconnect(final PeerDisconnectedSubscriber subscriber) {
    disconnectedSubscribers.subscribe(subscriber);
  }

  @Override
  public SafeFuture<RpcStream> sendRequest(
      final RpcMethod rpcMethod, final Bytes initialPayload, final RpcRequestHandler handler) {
    return SafeFuture.failedFuture(new OperationNotSupportedException());
  }

  @Override
  public boolean connectionInitiatedLocally() {
    return true;
  }

  @Override
  public boolean connectionInitiatedRemotely() {
    return false;
  }
}
