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

package tech.pegasys.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.bls.keystore.KeyStore;
import tech.pegasys.artemis.bls.keystore.KeyStoreLoader;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;

class DepositCommandTest {
  private static final int VALIDATORS_COUNT = 2;
  private static final String EXPECTED_PASSWORD = "testpassword";
  private DepositCommand.CommonParams commonParams;
  private DepositTransactionSender depositTransactionSender;
  private final DepositCommand depositCommand = new DepositCommand(exitCode -> {});

  @BeforeEach
  void setUp() {
    commonParams = mock(DepositCommand.CommonParams.class);
    depositTransactionSender = mock(DepositTransactionSender.class);

    when(commonParams.createTransactionSender()).thenReturn(depositTransactionSender);
    when(depositTransactionSender.sendDepositTransaction(any(), any(), any()))
        .thenReturn(completedFuture(null));
  }

  @Test
  void encryptedKeystoresAreCreatedWithPasswords(@TempDir final Path tempDir) {
    depositCommand.generate(
        commonParams,
        VALIDATORS_COUNT,
        tempDir.toString(),
        true,
        null,
        null,
        EXPECTED_PASSWORD,
        null,
        null,
        EXPECTED_PASSWORD);

    // assert that sub directories exist
    final File[] subDirectories = tempDir.toFile().listFiles();
    assertThat(subDirectories).hasSize(VALIDATORS_COUNT);
    Arrays.stream(subDirectories).forEach(file -> assertThat(file).isDirectory());

    for (int i = 0; i < subDirectories.length; i++) {
      assertKeyStoreFilesExist(subDirectories[i].toPath());
    }
  }

  @Test
  void encryptedKeystoresAreCreatedWithPasswordsFromFile(@TempDir final Path tempDir)
      throws IOException {
    final Path passwordFile = Files.writeString(tempDir.resolve("password.txt"), EXPECTED_PASSWORD);
    final Path ksDir = Files.createDirectory(tempDir.resolve("ksDir"));
    depositCommand.generate(
        commonParams,
        VALIDATORS_COUNT,
        ksDir.toString(),
        true,
        passwordFile.toFile(),
        null,
        null,
        passwordFile.toFile(),
        null,
        null);

    final File[] subDirectories = tempDir.toFile().listFiles();
    assertThat(subDirectories).hasSize(VALIDATORS_COUNT);
    Arrays.stream(subDirectories).forEach(file -> assertThat(file).isDirectory());

    for (int i = 0; i < subDirectories.length; i++) {
      assertKeyStoreFilesExist(subDirectories[i].toPath());
    }
  }

  private void assertKeyStoreFilesExist(final Path parentDir) {
    final File[] keyStoreFiles = parentDir.toFile().listFiles();
    assertThat(keyStoreFiles).hasSize(2);

    assertThat(
            KeyStore.validatePassword(
                EXPECTED_PASSWORD, KeyStoreLoader.loadFromFile(keyStoreFiles[0].toPath())))
        .isTrue();
    assertThat(
            KeyStore.validatePassword(
                EXPECTED_PASSWORD, KeyStoreLoader.loadFromFile(keyStoreFiles[1].toPath())))
        .isTrue();
  }
}
