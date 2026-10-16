/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.impl.BlsException;

public class SameMessageBatchVerifierTest {

  private static final Bytes OTHER_MESSAGE = Bytes.wrap("other message".getBytes(UTF_8));

  @Test
  void verifiesManySignaturesOverTheSameMessage() {
    final Bytes message = Bytes.wrap("same message".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(i);
      publicKeys.add(List.of(keyPair.getPublicKey()));
      messages.add(message);
      signatures.add(BLS.sign(keyPair.getSecretKey(), message));
    }

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isTrue();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isTrue();

    signatures.set(17, BLS.sign(BLSTestUtil.randomKeyPair(17).getSecretKey(), OTHER_MESSAGE));

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isFalse();
  }

  @Test
  void verifiesMixOfRepeatedAndDistinctMessages() {
    final Bytes m1 = Bytes.wrap("m1".getBytes(UTF_8));
    final Bytes m2 = Bytes.wrap("m2".getBytes(UTF_8));
    final Bytes m3 = Bytes.wrap("m3".getBytes(UTF_8));
    final Bytes m4 = Bytes.wrap("m4".getBytes(UTF_8));
    final List<Bytes> messages = List.of(m1, m2, m3, m1, m4, m2, m1);
    final List<BLSKeyPair> keyPairs = new ArrayList<>();
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < messages.size(); i++) {
      final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(100 + i);
      keyPairs.add(keyPair);
      publicKeys.add(List.of(keyPair.getPublicKey()));
      signatures.add(BLS.sign(keyPair.getSecretKey(), messages.get(i)));
    }

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isTrue();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isTrue();

    // An invalid signature inside the m1 group
    final List<BLSSignature> invalidInGroup = new ArrayList<>(signatures);
    invalidInGroup.set(3, BLS.sign(keyPairs.get(3).getSecretKey(), OTHER_MESSAGE));
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, invalidInGroup, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, invalidInGroup, true))
        .isFalse();

    // An invalid signature for the singleton m4
    final List<BLSSignature> invalidSingleton = new ArrayList<>(signatures);
    invalidSingleton.set(4, BLS.sign(keyPairs.get(4).getSecretKey(), OTHER_MESSAGE));
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, invalidSingleton, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, invalidSingleton, true))
        .isFalse();
  }

  @Test
  void returnsFalseForEmptyInput() {
    assertThat(SameMessageBatchVerifier.batchVerify(List.of(), List.of(), List.of(), false))
        .isFalse();
  }

  @Test
  void routesASingleEntryThroughFastAggregateVerify() {
    final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(7);
    final Bytes message = Bytes.wrap("single".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys = List.of(List.of(keyPair.getPublicKey()));

    assertThat(
            SameMessageBatchVerifier.batchVerify(
                publicKeys,
                List.of(message),
                List.of(BLS.sign(keyPair.getSecretKey(), message)),
                false))
        .isTrue();
    assertThat(
            SameMessageBatchVerifier.batchVerify(
                publicKeys,
                List.of(message),
                List.of(BLS.sign(keyPair.getSecretKey(), OTHER_MESSAGE)),
                false))
        .isFalse();
  }

  @Test
  void throwsBlsExceptionOnMismatchedListSizes() {
    final BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    final BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    final Bytes message = Bytes.wrap("message".getBytes(UTF_8));

    assertThatThrownBy(
            () ->
                SameMessageBatchVerifier.batchVerify(
                    List.of(List.of(keyPair1.getPublicKey()), List.of(keyPair2.getPublicKey())),
                    List.of(message),
                    List.of(
                        BLS.sign(keyPair1.getSecretKey(), message),
                        BLS.sign(keyPair2.getSecretKey(), message)),
                    false))
        .isInstanceOf(BlsException.class);
  }

  @Test
  void returnsTrueWhenVerificationIsDisabled() {
    final BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    final BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    final Bytes message = Bytes.wrap("message".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys =
        List.of(List.of(keyPair1.getPublicKey()), List.of(keyPair2.getPublicKey()));
    final List<Bytes> messages = List.of(message, message);
    final List<BLSSignature> wrongSignatures =
        List.of(
            BLS.sign(keyPair1.getSecretKey(), OTHER_MESSAGE),
            BLS.sign(keyPair2.getSecretKey(), OTHER_MESSAGE));

    BLSConstants.disableBLSVerification();
    try {
      assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, wrongSignatures, false))
          .isTrue();
    } finally {
      BLSConstants.enableBLSVerification();
    }
  }

  @Test
  void returnsFalseForAnUndecodableSignature() {
    final Bytes message = Bytes.wrap("same message".getBytes(UTF_8));
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(200 + i);
      publicKeys.add(List.of(keyPair.getPublicKey()));
      messages.add(message);
      signatures.add(BLS.sign(keyPair.getSecretKey(), message));
    }
    signatures.set(2, BLSSignature.fromBytesCompressed(Bytes.random(96)));

    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, false))
        .isFalse();
    assertThat(SameMessageBatchVerifier.batchVerify(publicKeys, messages, signatures, true))
        .isFalse();
  }
}
