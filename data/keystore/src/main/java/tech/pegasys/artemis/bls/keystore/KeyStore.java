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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import tech.pegasys.artemis.bls.keystore.builder.ChecksumBuilder;
import tech.pegasys.artemis.bls.keystore.builder.CipherBuilder;
import tech.pegasys.artemis.bls.keystore.builder.KdfBuilder;
import tech.pegasys.artemis.bls.keystore.builder.KeyStoreDataBuilder;
import tech.pegasys.artemis.util.message.BouncyCastleMessageDigestFactory;

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

  public static KeyStore encrypt(
      final Bytes secret,
      final String password,
      final String path,
      final KdfParam kdfParam,
      final CipherParam cipherParam) {

    final Bytes decryptionKey = kdfParam.decryptionKey(password.getBytes(UTF_8));
    final SecretKeySpec secretKey =
        new SecretKeySpec(decryptionKey.slice(0, 16).toArrayUnsafe(), "AES");

    final IvParameterSpec ivParameterSpec =
        new IvParameterSpec(cipherParam.getIv().toArrayUnsafe());

    final Bytes cipherMessage;
    final Bytes checksumMessage;
    try {
      final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding", BC);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
      final Bytes intermediateEncryptedBytes = Bytes.wrap(cipher.update(secret.toArrayUnsafe()));
      cipherMessage = Bytes.wrap(intermediateEncryptedBytes, Bytes.wrap(cipher.doFinal()));

      final Bytes checksumBytes = Bytes.wrap(decryptionKey.slice(16, 16), cipherMessage);
      final MessageDigest messageDigest = sha256Digest();
      checksumBytes.update(messageDigest);
      checksumMessage = Bytes.wrap(messageDigest.digest());
    } catch (final GeneralSecurityException e) {
      throw new RuntimeException("Error applying aes-128-ctr cipher function", e);
    }

    final Checksum checksum = ChecksumBuilder.aChecksum().withMessage(checksumMessage).build();
    final tech.pegasys.artemis.bls.keystore.Cipher cipher =
        CipherBuilder.aCipher().withCipherParam(cipherParam).withMessage(cipherMessage).build();
    final Kdf kdf = KdfBuilder.aKdf().withParam(kdfParam).build();
    final Crypto crypto = new Crypto(kdf, checksum, cipher);

    Bytes pubKey = Bytes.random(32); // TODO: Obtain Public Key from private key (and path?)
    final KeyStoreData keyStoreData =
        KeyStoreDataBuilder.aKeyStoreData()
            .withCrypto(crypto)
            .withPath(path)
            .withPubkey(pubKey)
            .build();

    return new KeyStore(keyStoreData);
  }

  public boolean validatePassword(final String password) {
    final Bytes decryptionKey =
        keyStoreData.getCrypto().getKdf().getParam().decryptionKey(password.getBytes(UTF_8));
    final Bytes dkSlice = decryptionKey.slice(16, 16);
    final Bytes preImage = Bytes.wrap(dkSlice, keyStoreData.getCrypto().getCipher().getMessage());
    final MessageDigest messageDigest = sha256Digest();
    preImage.update(messageDigest);
    final Bytes checksum = Bytes.wrap(messageDigest.digest());

    return Objects.equals(checksum, keyStoreData.getCrypto().getChecksum().getMessage());
  }

  public Bytes decrypt(final String password) {
    if (!validatePassword(password)) {
      throw new RuntimeException("Invalid password");
    }

    final Bytes decryptionKey =
        keyStoreData.getCrypto().getKdf().getParam().decryptionKey(password.getBytes(UTF_8));

    return keyStoreData.getCrypto().getCipher().decrypt(decryptionKey);
  }

  private static MessageDigest sha256Digest() {
    final MessageDigest messageDigest;
    try {
      messageDigest = BouncyCastleMessageDigestFactory.create("sha256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Unable to create message digest", e);
    }
    return messageDigest;
  }
}
