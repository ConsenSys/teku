/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.validation.signatures;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;

/** Verifies signatures synchronously and counts how many verify calls it received. */
public class RecordingSignatureVerifier implements AsyncBLSSignatureVerifier {
  private final AsyncBLSSignatureVerifier delegate =
      AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE);
  private final AtomicInteger verifyCount = new AtomicInteger();

  public int getVerifyCount() {
    return verifyCount.get();
  }

  @Override
  public SafeFuture<Boolean> verify(
      final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
    verifyCount.incrementAndGet();
    return delegate.verify(publicKeys, message, signature);
  }

  @Override
  public SafeFuture<Boolean> verify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures) {
    verifyCount.incrementAndGet();
    return delegate.verify(publicKeys, messages, signatures);
  }
}
