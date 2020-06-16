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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.AbstractRpcByteBufDecoder;

public class NoopFrameDecoder extends AbstractRpcByteBufDecoder<ByteBuf> {
  private final int expectedBytes;

  public NoopFrameDecoder(int expectedBytes) {
    this.expectedBytes = expectedBytes;
  }

  @Override
  protected Optional<ByteBuf> decodeOneImpl(ByteBuf in) {
    if (in.readableBytes() < expectedBytes) {
      return Optional.empty();
    }
    return Optional.of(in.readRetainedSlice(expectedBytes));
  }

  public int getExpectedBytes() {
    return expectedBytes;
  }
}
