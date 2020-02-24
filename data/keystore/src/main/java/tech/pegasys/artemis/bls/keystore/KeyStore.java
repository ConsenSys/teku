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

package tech.pegasys.artemis.bls.keystore;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.apache.tuweni.crypto.Hash.sha2_256;

import com.google.common.annotations.VisibleForTesting;
import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import tech.pegasys.artemis.bls.keystore.builder.ChecksumBuilder;
import tech.pegasys.artemis.bls.keystore.builder.CipherBuilder;
import tech.pegasys.artemis.bls.keystore.builder.KdfBuilder;
import tech.pegasys.artemis.bls.keystore.builder.KeyStoreDataBuilder;
import tech.pegasys.artemis.bls.keystore.model.Checksum;
import tech.pegasys.artemis.bls.keystore.model.CipherParam;
import tech.pegasys.artemis.bls.keystore.model.Crypto;
import tech.pegasys.artemis.bls.keystore.model.Kdf;
import tech.pegasys.artemis.bls.keystore.model.KdfParam;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.util.mikuli.PublicKey;
import tech.pegasys.artemis.util.mikuli.SecretKey;

/**
 * BLS Key Store implementation EIP-2335
 *
 * @see <a href="https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2335.md">EIP-2335</a>
 */
public class KeyStore {
  private static final BouncyCastleProvider BC = new BouncyCastleProvider();
  private final KeyStoreData keyStoreData;

  public KeyStore(final KeyStoreData keyStoreData) {
    this.keyStoreData = keyStoreData;
  }

  public KeyStoreData getKeyStoreData() {
    return keyStoreData;
  }

  /**
   * Encrypt the given BLS12-381 key with specified password.
   *
   * @param blsPrivateKey BLS12-381 private key in Bytes
   * @param password The password to use for encryption
   * @param path Path as defined in EIP-2334. Can be empty String.
   * @param kdfParam crypto function such as scrypt or PBKDF2 and related parameters such as dklen,
   *     salt etc.
   * @param cipherParam cipher function and parameters such as iv.
   * @return The constructed KeyStore with encrypted blsPrivateKey and other details as defined by
   *     the EIP-2335 standard.
   */
  public static KeyStore encrypt(
      final Bytes blsPrivateKey,
      final String password,
      final String path,
      final KdfParam kdfParam,
      final CipherParam cipherParam) {

    final Crypto crypto =
        encryptUsingCipherFunction(blsPrivateKey, password, kdfParam, cipherParam);
    final Bytes pubKey =
        new PublicKey(SecretKey.fromBytes(Bytes48.leftPad(blsPrivateKey))).toBytesCompressed();
    final KeyStoreData keyStoreData =
        KeyStoreDataBuilder.aKeyStoreData()
            .withCrypto(crypto)
            .withPath(path)
            .withPubkey(pubKey)
            .build();

    return new KeyStore(keyStoreData);
  }

  @VisibleForTesting
  static Crypto encryptUsingCipherFunction(
      final Bytes secret,
      final String password,
      final KdfParam kdfParam,
      final CipherParam cipherParam) {
    checkArgument(
        kdfParam.getDerivedKeyLength() > 16, "aes-128-ctr requires kdf dklen greater than 16");

    final Bytes decryptionKey = DecryptionKeyGenerator.generate(password.getBytes(UTF_8), kdfParam);
    final Bytes cipherMessage =
        applyCipherFunction(decryptionKey, cipherParam.getIv(), true, secret.toArrayUnsafe());
    final Bytes checksumMessage = calculateChecksum(kdfParam, decryptionKey, cipherMessage);
    final Checksum checksum = ChecksumBuilder.aChecksum().withMessage(checksumMessage).build();
    final tech.pegasys.artemis.bls.keystore.model.Cipher cipher =
        CipherBuilder.aCipher().withCipherParam(cipherParam).withMessage(cipherMessage).build();
    final Kdf kdf = KdfBuilder.aKdf().withParam(kdfParam).build();
    return new Crypto(kdf, checksum, cipher);
  }

  public Bytes decrypt(final String password) {
    Objects.requireNonNull(password);
    final KdfParam kdfParam = keyStoreData.getCrypto().getKdf().getParam();
    checkArgument(
        kdfParam.getDerivedKeyLength() > 16, "aes-128-ctr requires kdf dklen greater than 16");

    final Bytes decryptionKey = DecryptionKeyGenerator.generate(password.getBytes(UTF_8), kdfParam);

    if (!validateChecksum(decryptionKey)) {
      throw new RuntimeException("Invalid checksum");
    }

    final Bytes iv = keyStoreData.getCrypto().getCipher().getCipherParam().getIv();
    final Bytes encryptedMessage = keyStoreData.getCrypto().getCipher().getMessage();
    return applyCipherFunction(decryptionKey, iv, false, encryptedMessage.toArrayUnsafe());
  }

  public boolean validatePassword(final String password) {
    final Bytes decryptionKey =
        DecryptionKeyGenerator.generate(
            password.getBytes(UTF_8), keyStoreData.getCrypto().getKdf().getParam());
    return validateChecksum(decryptionKey);
  }

  private boolean validateChecksum(final Bytes decryptionKey) {
    final Bytes checksum =
        calculateChecksum(
            keyStoreData.getCrypto().getKdf().getParam(),
            decryptionKey,
            keyStoreData.getCrypto().getCipher().getMessage());
    return Objects.equals(checksum, keyStoreData.getCrypto().getChecksum().getMessage());
  }

  private static Bytes calculateChecksum(
      final KdfParam kdfParam, final Bytes decryptionKey, final Bytes cipherMessage) {
    // aes-128-ctr needs first 16 bytes for its key. The rest of the key is used to create checksum
    final Bytes dkSliceSecondHalf = decryptionKey.slice(16, kdfParam.getDerivedKeyLength() - 16);
    return sha2_256(Bytes.wrap(dkSliceSecondHalf, cipherMessage));
  }

  private static Bytes applyCipherFunction(
      final Bytes decryptionKey, final Bytes iv, boolean encryptMode, final byte[] inputMessage) {
    // aes-128-ctr requires a key size of 16. Use first 16 bytes of decryption key as AES key,
    // rest of the decryption key will be used to create checksum later on
    final SecretKeySpec secretKey =
        new SecretKeySpec(decryptionKey.slice(0, 16).toArrayUnsafe(), "AES");

    final IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.toArrayUnsafe());
    Bytes cipherMessage = Bytes.EMPTY;
    try {
      final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding", BC);
      cipher.init(encryptMode ? ENCRYPT_MODE : DECRYPT_MODE, secretKey, ivParameterSpec);

      int blockSize = cipher.getBlockSize(); // AES cipher block size is 16.
      int blockIndex = 0;
      while (blockIndex < inputMessage.length - blockSize) {
        cipherMessage =
            Bytes.wrap(
                cipherMessage, Bytes.wrap(cipher.update(inputMessage, blockIndex, blockSize)));
        blockIndex += blockSize;
      }

      return Bytes.wrap(
          cipherMessage,
          Bytes.wrap(cipher.doFinal(inputMessage, blockIndex, inputMessage.length - blockIndex)));
    } catch (final GeneralSecurityException e) {
      throw new RuntimeException("Error applying aes-128-ctr cipher function", e);
    }
  }
}
