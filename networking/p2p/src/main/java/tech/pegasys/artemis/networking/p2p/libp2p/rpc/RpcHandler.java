/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.p2p.libp2p.rpc;

import io.libp2p.core.Connection;
import io.libp2p.core.P2PAbstractChannel;
import io.libp2p.core.multistream.Mode;
import io.libp2p.core.multistream.Multistream;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.multistream.ProtocolMatcher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.networking.p2p.libp2p.LibP2PNodeId;
import tech.pegasys.artemis.networking.p2p.libp2p.rpc.RpcHandler.Controller;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.rpc.RpcDataHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcMethod;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.util.async.SafeFuture;

public class RpcHandler implements ProtocolBinding<Controller> {
  private static final Logger LOG = LogManager.getLogger();

  private final RpcMethod rpcMethod;

  public RpcHandler(RpcMethod rpcMethod) {
    this.rpcMethod = rpcMethod;
  }

  @SuppressWarnings("unchecked")
  public SafeFuture<RpcStream> sendRequest(
      Connection connection, Bytes initialPayload, RpcDataHandler handler) {
    return SafeFuture.of(
            connection
                .getMuxerSession()
                .createStream(
                    Multistream.create(this.toInitiator(rpcMethod.getId())).toStreamHandler())
                .getControler())
        .thenCompose(
            ctr -> {
              ctr.setDataHandler(handler);
              return ctr.getRpcStream()
                  .writeBytes(initialPayload)
                  .thenApply(f -> ctr.getRpcStream());
            });
  }

  @NotNull
  @Override
  public String getAnnounce() {
    return rpcMethod.getId();
  }

  @NotNull
  @Override
  public ProtocolMatcher getMatcher() {
    return new ProtocolMatcher(Mode.STRICT, getAnnounce(), null);
  }

  @NotNull
  @Override
  public SafeFuture<Controller> initChannel(P2PAbstractChannel channel, String s) {
    // TODO timeout handlers
    final Connection connection = ((io.libp2p.core.Stream) channel).getConn();
    final NodeId nodeId = new LibP2PNodeId(connection.getSecureSession().getRemoteId());
    Controller controller = new Controller(nodeId);
    if (!channel.isInitiator()) {
      controller.setDataHandler(rpcMethod.createIncomingRequestHandler());
    }
    channel.getNettyChannel().pipeline().addLast(controller);
    return controller.activeFuture;
  }

  static class Controller extends SimpleChannelInboundHandler<ByteBuf> {
    private final NodeId nodeId;
    private RpcDataHandler rpcDataHandler;
    private RpcStream rpcStream;
    private List<ByteBuf> bufferedData = new ArrayList<>();

    protected final SafeFuture<Controller> activeFuture = new SafeFuture<>();

    private Controller(final NodeId nodeId) {
      this.nodeId = nodeId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
      rpcStream = new LibP2PRpcStream(ctx);
      activeFuture.complete(this);
    }

    public RpcStream getRpcStream() {
      return rpcStream;
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf msg) {
      if (rpcDataHandler != null) {
        rpcDataHandler.onData(nodeId, rpcStream, msg);
      } else {
        bufferedData.add(msg);
      }
    }

    public void setDataHandler(RpcDataHandler dataHandler) {
      if (rpcDataHandler != null) {
        throw new IllegalStateException("Attempt to set an already set data handler");
      }
      rpcDataHandler = dataHandler;
      while (!bufferedData.isEmpty()) {
        ByteBuf currentBuffer = bufferedData.remove(0);
        rpcDataHandler.onData(nodeId, rpcStream, currentBuffer);
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      LOG.error("Unhandled error while processes req/response", cause);
      final IllegalStateException exception = new IllegalStateException("Channel exception", cause);
      activeFuture.completeExceptionally(exception);
      rpcStream.closeStream().reportExceptions();
    }
  }
}
