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
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.DisposedDecompressorException;

public interface Compressor {

  interface Decompressor {

    /**
     * @param input
     * @return
     * @throws DisposedDecompressorException
     */
    Optional<ByteBuf> uncompress(final ByteBuf input) throws CompressionException;

    /**
     * @return
     * @throws DisposedDecompressorException
     */
    void complete() throws CompressionException;
  }

  /**
   * Returns the compressed data
   *
   * @param data The data to compress
   * @return The compressed data
   */
  Bytes compress(final Bytes data);

  Decompressor createDecompressor(final int uncompressedPayloadSize);

  /**
   * Returns a maximum estimate of the size of a compressed payload given the uncompressed payload
   * size.
   *
   * @param uncompressedLength The size of an uncompressed payload.
   * @return The maximum size of a payload of size {@code uncompressedLength} after compression.
   */
  int getMaxCompressedLength(final int uncompressedLength);
}
