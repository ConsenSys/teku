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

package tech.pegasys.artemis.util.mikuli;

import static tech.pegasys.artemis.util.mikuli.G2Point.hashToG2;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;

/*
 * (Heavily) adapted from the ConsenSys/mikuli (Apache 2 License) implementation:
 * https://github.com/ConsenSys/mikuli/blob/master/src/main/java/net/consensys/mikuli/crypto/*.java
 */

/**
 * This Boneh-Lynn-Shacham (BLS) signature implementation is constructed from a pairing friendly
 * BLS12-381 elliptic curve. This implementation implements a subset of the functions from the
 * proposed IETF standard.
 *
 * <p>Reference: https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00
 *
 * <p>This class depends upon the Apache Milagro library being available. See
 * https://milagro.apache.org.
 *
 * <p>Apache Milagro can be included using the gradle dependency
 * 'org.miracl.milagro.amcl:milagro-crypto-java'.
 */
public final class BLS12381 {

  // private static final String domainSeparationTag = "BLS_SIG_BLS12381G2-SHA256-SSWU-RO-_POP_";

  /*
   * Methods used directly in the Ethereum 2.0 specifications.
   */

  /**
   * Generates a Signature from a private key and message.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.2.1
   *
   * @param secretKey The secret key, not null
   * @param message The message to sign, not null
   * @return The Signature, not null
   */
  public static Signature sign(SecretKey secretKey, Bytes message) {
    G2Point hashInGroup2 = hashToG2(message);
    return new Signature(secretKey.sign(hashInGroup2));
  }

  /**
   * Verifies the given BLS signature against the message bytes using the public key.
   *
   * <p>Implements the basic scheme of
   * https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.1 which is identical to
   * CoreVerify().
   *
   * @param publicKey The public key, not null
   * @param message The message data to verify, not null
   * @param signature The signature, not null
   * @return True if the verification is successful, false otherwise.
   */
  public static boolean verify(PublicKey publicKey, Bytes message, Signature signature) {
    return coreVerify(publicKey, message, signature);
  }

  /**
   * Aggregates a list of signatures into a single signature using BLS magic.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-2.7
   *
   * @param signatures the list of signatures to be aggregated
   * @return the aggregated signature
   */
  public static Signature aggregate(List<Signature> signatures) {
    return new Signature(Signature.aggregate(signatures));
  }

  /**
   * Verifies an aggregate BLS signature against a message using the list of public keys.
   *
   * <p>Implements https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-3.3.4
   *
   * @param publicKeys The list of public keys, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  public static boolean fastAggregateVerify(
      List<PublicKey> publicKeys, Bytes message, Signature signature) {
    return verify(PublicKey.aggregate(publicKeys), message, signature);
  }

  /*
   * Other methods defined by the standard and used above.
   */

  /**
   * The CoreVerify algorithm checks that a signature is valid for the octet string message under
   * the public key publicKey.
   *
   * <p>https://tools.ietf.org/html/draft-irtf-cfrg-bls-signature-00#section-2.6
   *
   * @param publicKey The public key, not null
   * @param message The message data to verify, not null
   * @param signature The aggregate signature, not null
   * @return True if the verification is successful, false otherwise
   */
  private static boolean coreVerify(PublicKey publicKey, Bytes message, Signature signature) {
    G2Point hashInGroup2 = hashToG2(message);
    return signature.verify(publicKey, hashInGroup2);
  }
}
