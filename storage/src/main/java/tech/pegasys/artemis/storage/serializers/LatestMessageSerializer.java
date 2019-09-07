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

package tech.pegasys.artemis.storage.serializers;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import tech.pegasys.artemis.storage.LatestMessage;

public class LatestMessageSerializer implements Serializer<LatestMessage> {

  @Override
  public void serialize(final DataOutput2 out, final LatestMessage value) throws IOException {
    out.writeLong(value.getEpoch().longValue());
    out.writeChars(value.getRoot().toHexString());
  }

  @Override
  public LatestMessage deserialize(final DataInput2 input, final int available) throws IOException {
    return new LatestMessage(
        UnsignedLong.fromLongBits(input.readLong()), Bytes32.fromHexString(input.readLine()));
  }
}
