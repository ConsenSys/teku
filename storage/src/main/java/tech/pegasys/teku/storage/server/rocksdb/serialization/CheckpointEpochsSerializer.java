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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;

class CheckpointEpochsSerializer implements RocksDbSerializer<CheckpointEpochs> {

  @Override
  public CheckpointEpochs deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final UInt64 justifiedEpoch = UInt64.fromLongBits(reader.readUInt64());
          final UInt64 finalizedEpoch = UInt64.fromLongBits(reader.readUInt64());
          return new CheckpointEpochs(justifiedEpoch, finalizedEpoch);
        });
  }

  @Override
  public byte[] serialize(final CheckpointEpochs value) {
    return SSZ.encode(
            writer -> {
              writer.writeUInt64(value.getJustifiedEpoch().longValue());
              writer.writeUInt64(value.getFinalizedEpoch().longValue());
            })
        .toArrayUnsafe();
  }
}
