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

package tech.pegasys.teku.ssz.backing.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitlistSchema;

class BitvectorImplImplTest {

  private static int testBitvectorLength = 4;

  private static BitvectorImpl createBitvector() {
    return new BitvectorImpl(testBitvectorLength, 0, 3);
  }

  @Test
  void initTest() {
    BitvectorImpl BitvectorImpl = new BitvectorImpl(10);
    Assertions.assertEquals(BitvectorImpl.getBit(0), false);
    Assertions.assertEquals(BitvectorImpl.getBit(9), false);
  }

  @Test
  void setTest() {
    BitvectorImpl BitvectorImpl = new BitvectorImpl(10, 1, 3, 8);

    Assertions.assertEquals(BitvectorImpl.getBit(0), false);
    Assertions.assertEquals(BitvectorImpl.getBit(1), true);
    Assertions.assertEquals(BitvectorImpl.getBit(3), true);
    Assertions.assertEquals(BitvectorImpl.getBit(4), false);
    Assertions.assertEquals(BitvectorImpl.getBit(8), true);
  }

  @Test
  void serializationTest() {
    BitvectorImpl BitvectorImpl = createBitvector();

    Bytes BitvectorImplSerialized = BitvectorImpl.serialize();
    Assertions.assertEquals(BitvectorImplSerialized.toHexString(), "0x09");
  }

  @Test
  void deserializationTest() {
    BitvectorImpl BitvectorImpl = createBitvector();

    Bytes BitvectorImplSerialized = BitvectorImpl.serialize();
    BitvectorImpl newBitvectorImpl =
        BitvectorImpl.fromBytes(BitvectorImplSerialized, testBitvectorLength);
    Assertions.assertEquals(BitvectorImpl, newBitvectorImpl);
  }

  @Test
  public void deserializationEmptyBytesTest() {
    final BitvectorImpl result = BitvectorImpl.fromBytes(Bytes.EMPTY, 0);
    assertThat(result.getSize()).isZero();
  }

  @Test
  public void deserializationNotEnoughBytes() {
    assertThatThrownBy(() -> BitvectorImpl.fromBytes(Bytes.of(1, 2, 3), 50))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Incorrect data size");
  }

  @Test
  public void deserializationTooManyBytes() {
    assertThatThrownBy(() -> BitvectorImpl.fromBytes(Bytes.of(1, 2, 3), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Incorrect data size");
  }

  @Test
  void bitlistHashTest() {
    Bytes32 hashOld =
        Bytes32.fromHexString("0x447ac4def72d4aa09ded8e1130cbe013511d4881c3393903ada630f034e985d7");

    SszBitlist sszBitlist =
        SszBitlistSchema.create(2048).ofBits(2048, IntStream.range(0, 44).toArray());
    Bytes32 hashNew = sszBitlist.hashTreeRoot();

    Assertions.assertEquals(hashOld, hashNew);
  }
}
