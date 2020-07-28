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

package tech.pegasys.teku.bls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;

class BLSSignatureTest {

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameEmptySignature() {
    BLSSignature signature = BLSSignature.empty();
    assertEquals(signature, signature);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTwoEmptySignatures() {
    BLSSignature signature1 = BLSSignature.empty();
    BLSSignature signature2 = BLSSignature.empty();
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsIfSerializationOfEmptySignatureIsCorrect() {
    BLSSignature emptySignature = BLSSignature.empty();
    assertEquals(
        "0x0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000",
        emptySignature.toSSZBytes().toHexString());
  }

  @Test
  void succeedsIfDeserializationOfEmptySignatureIsCorrect() {
    BLSSignature emptySignature = BLSSignature.empty();
    Bytes zeroBytes = Bytes.wrap(new byte[96]);
    Bytes emptyBytesSsz = SSZ.encode(writer -> writer.writeFixedBytes(zeroBytes));
    BLSSignature deserializedSignature = BLSSignature.fromSSZBytes(emptyBytesSsz);
    assertEquals(emptySignature, deserializedSignature);
  }

  @Test
  void succeedsIfDeserializationThrowsWithTooFewBytes() {
    Bytes tooFewBytes = Bytes.wrap(new byte[95]);
    assertThrows(IllegalArgumentException.class, () -> BLSSignature.fromSSZBytes(tooFewBytes));
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameSignature() {
    BLSSignature signature = BLSSignature.random(42);
    assertEquals(signature, signature);
    assertEquals(signature.hashCode(), signature.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentSignatures() {
    BLSSignature signature1 = BLSSignature.random(42);
    BLSSignature signature2 = BLSSignature.random(43);
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheSameSignature() {
    BLSSignature signature1 = BLSSignature.random(65);
    BLSSignature signature2 = BLSSignature.fromSSZBytes(signature1.toSSZBytes());
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheEmptySignature() {
    BLSSignature signature1 = BLSSignature.empty();
    BLSSignature signature2 = BLSSignature.fromSSZBytes(signature1.toSSZBytes());
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForEmptySignatures() {
    assertEquals(BLSSignature.empty(), BLSSignature.empty());
    assertEquals(BLSSignature.empty().hashCode(), BLSSignature.empty().hashCode());
  }

  @Test
  void succeedsWhenPassingEmptyListToAggregateSignaturesDoesNotThrowException() {
    assertDoesNotThrow(() -> BLS.aggregateSignatures(Collections.emptyList()));
  }

  @Test
  void aggregateSignatures_aggregateEmptyListAndAggregate() {
    BLSSignature emptySig = BLS.aggregateSignatures(Collections.emptyList());
    BLSSignature sig = BLSSignature.random(1);
    BLSSignature aggrSig = BLS.aggregateSignatures(List.of(emptySig, sig));
    assertThat(aggrSig).isEqualTo(sig);
  }

  @Test
  void roundtripEncodeDecodeCompressed() {
    BLSSignature signature = BLSSignature.random(513);
    final BLSSignature result = BLSSignature.fromSSZBytes(signature.toSSZBytes());
    assertEquals(signature, result);
    assertEquals(signature.hashCode(), result.hashCode());
  }

  @Test
  void testRandomFromSeed() {
    BLSSignature randomSig = BLSSignature.random(92892840);
    System.out.println(randomSig);
  }
}
