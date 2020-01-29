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

package tech.pegasys.artemis.util.bls;

import tech.pegasys.artemis.util.mikuli.KeyPair;

public final class BLSKeyPair {

  /**
   * Generate a key pair based on a randomly generated secret key.
   *
   * <p>This uses low-grade randomness and MUST NOT be used to generate production keys.
   *
   * @return a random key pair
   */
  public static BLSKeyPair random() {
    return new BLSKeyPair(KeyPair.random());
  }

  /**
   * Generate a key pair based on a secret key generated from a seed value.
   *
   * <p>This MUST NOT be used to generate production keys.
   *
   * @return a keypair generated from a seed
   */
  public static BLSKeyPair random(int seed) {
    return new BLSKeyPair(KeyPair.random(seed));
  }

  private final BLSPublicKey publicKey;
  private final BLSSecretKey secretKey;

  /**
   * Construct from BLSPublicKey and BLSSecretKey
   *
   * @param publicKey a BLS public key
   * @param secretKey a BLS secret key
   */
  public BLSKeyPair(BLSPublicKey publicKey, BLSSecretKey secretKey) {
    this.publicKey = publicKey;
    this.secretKey = secretKey;
  }

  /**
   * Construct from a BLS secret key alone.
   *
   * @param secretKey a BLS secret key
   */
  public BLSKeyPair(BLSSecretKey secretKey) {
    this(new BLSPublicKey(secretKey), secretKey);
  }

  /**
   * Construct from a Mikuli key pair.
   *
   * @param keyPair a Mikuli key pair
   */
  BLSKeyPair(KeyPair keyPair) {
    this(new BLSPublicKey(keyPair.publicKey()), new BLSSecretKey(keyPair.secretKey()));
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public BLSSecretKey getSecretKey() {
    return secretKey;
  }
}
