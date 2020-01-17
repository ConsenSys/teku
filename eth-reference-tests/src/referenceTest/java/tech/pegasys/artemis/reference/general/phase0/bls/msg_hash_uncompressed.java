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

package tech.pegasys.artemis.reference.general.phase0.bls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.artemis.util.hashToG2.HashToCurve.hashToG2;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.util.mikuli.G2Point;

class msg_hash_uncompressed extends TestSuite {

  // The msg_hash_g2_uncompressed handler should hash the message to G2,
  // without compression, and the result should match the expected output.
  @ParameterizedTest(name = "{index}. message hash to G2 uncompressed {0} -> {1}")
  @MethodSource("readMessageHashG2Uncompressed")
  void messageHashToG2Uncompressed(Bytes message, G2Point g2PointExpected) {
    // TODO: The cipher suite in the test generator is wrong. Update here when fixed.
    // G2Point g2PointActual = G2Point.hashToG2(message);
    G2Point g2PointActual =
        new G2Point(
            hashToG2(
                message,
                Bytes.wrap(
                    "BLS_SIG_BLS12381G2-SHA256-SSWU-RO_POP_".getBytes(StandardCharsets.UTF_8))));
    assertEquals(g2PointExpected, g2PointActual);
  }

  @MustBeClosed
  static Stream<Arguments> readMessageHashG2Uncompressed() {
    Path path = Paths.get("/general/phase0/bls/msg_hash_uncompressed/small");
    return messageHashUncompressedSetup(path);
  }
}
