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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.FluxSink;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Netty interface handler for incoming packets in form of raw bytes data wrapped as {@link
 * BytesValue} Implementation forwards all incoming packets in {@link FluxSink} provided via
 * constructor, so it could be later linked to processor to form incoming messages stream
 */
public class IncomingMessageSink extends SimpleChannelInboundHandler<BytesValue> {
  private static final Logger logger = LogManager.getLogger(IncomingMessageSink.class);
  private final FluxSink<BytesValue> bytesValueSink;

  public IncomingMessageSink(FluxSink<BytesValue> bytesValueSink) {
    this.bytesValueSink = bytesValueSink;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BytesValue msg) throws Exception {
    logger.trace(() -> String.format("Incoming packet %s in session %s", msg, ctx));
    bytesValueSink.next(msg);
  }
}
