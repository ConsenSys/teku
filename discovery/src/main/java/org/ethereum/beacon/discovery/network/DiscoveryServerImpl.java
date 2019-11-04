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

package org.ethereum.beacon.discovery.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.schedulers.Scheduler;
import org.reactivestreams.Publisher;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

// import tech.pegasys.artemis.util.bytes.Bytes4;
// import tech.pegasys.artemis.util.bytes.Bytes;

public class DiscoveryServerImpl implements DiscoveryServer {
  private static final int RECREATION_TIMEOUT = 5000;
  private static final int STOPPING_TIMEOUT = 10000;
  private static final Logger logger = LogManager.getLogger(DiscoveryServerImpl.class);
  private final ReplayProcessor<Bytes> incomingPackets = ReplayProcessor.cacheLast();
  private final FluxSink<Bytes> incomingSink = incomingPackets.sink();
  private final Integer udpListenPort;
  private final String udpListenHost;
  private AtomicBoolean listen = new AtomicBoolean(true);
  private Channel channel;

  public DiscoveryServerImpl(Bytes udpListenHost, Integer udpListenPort) {
    try {
      this.udpListenHost = InetAddress.getByAddress(udpListenHost.toArray()).getHostAddress();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    this.udpListenPort = udpListenPort;
  }

  @Override
  public void start(Scheduler scheduler) {
    logger.info(String.format("Starting discovery server on UDP port %s", udpListenPort));
    scheduler.execute(this::serverLoop);
  }

  private void serverLoop() {
    NioEventLoopGroup group = new NioEventLoopGroup(1);
    try {
      while (listen.get()) {
        Bootstrap b = new Bootstrap();
        b.group(group)
            .channel(NioDatagramChannel.class)
            .handler(
                new ChannelInitializer<NioDatagramChannel>() {
                  @Override
                  public void initChannel(NioDatagramChannel ch) throws Exception {
                    ch.pipeline()
                        .addLast(new DatagramToBytesValue())
                        .addLast(new IncomingMessageSink(incomingSink));
                  }
                });

        channel = b.bind(udpListenHost, udpListenPort).sync().channel();
        channel.closeFuture().sync();

        if (!listen.get()) {
          logger.info("Shutting down discovery server");
          break;
        }
        logger.error("Discovery server closed. Trying to restore after %s seconds delay");
        Thread.sleep(RECREATION_TIMEOUT);
      }
    } catch (Exception e) {
      logger.error("Can't start discovery server", e);
    } finally {
      try {
        group.shutdownGracefully().sync();
      } catch (Exception ex) {
        logger.error("Failed to shutdown discovery sever thread group", ex);
      }
    }
  }

  @Override
  public Publisher<Bytes> getIncomingPackets() {
    return incomingPackets;
  }

  @Override
  public void stop() {
    if (listen.get()) {
      logger.info("Stopping discovery server");
      listen.set(false);
      if (channel != null) {
        try {
          channel.close().await(STOPPING_TIMEOUT);
        } catch (InterruptedException ex) {
          logger.error("Failed to stop discovery server", ex);
        }
      }
    } else {
      logger.warn("An attempt to stop already stopping/stopped discovery server");
    }
  }
}
