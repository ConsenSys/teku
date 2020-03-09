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
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.artemis.bls.keystore.KeyStore;
import tech.pegasys.artemis.bls.keystore.KeyStoreLoader;
import tech.pegasys.artemis.cli.deposit.ValidatorKeysPasswordGroup;
import tech.pegasys.artemis.cli.deposit.WithdrawalKeysPasswordGroup;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;

class DepositCommandTest {
  private static final int VALIDATORS_COUNT = 2;
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
    final ValidatorKeysPasswordGroup validatorKeysPasswordGroup = new ValidatorKeysPasswordGroup();
    validatorKeysPasswordGroup.setPassword("testpassword");
    final WithdrawalKeysPasswordGroup withdrawalKeysPasswordGroup =
        new WithdrawalKeysPasswordGroup();
    withdrawalKeysPasswordGroup.setPassword("testpassword");

    depositCommand.generate(
        commonParams,
        VALIDATORS_COUNT,
        tempDir.toString(),
        true,
        validatorKeysPasswordGroup.readPasswordFromFile(),
        validatorKeysPasswordGroup.readPasswordFromEnvironmentVariable(),
        validatorKeysPasswordGroup.readPasswordInteractively(),
        withdrawalKeysPasswordGroup.readPasswordFromFile(),
        withdrawalKeysPasswordGroup.readPasswordFromEnvironmentVariable(),
        withdrawalKeysPasswordGroup.readPasswordInteractively());

    // assert that sub directories exist
    final File[] subDirectories =
        IntStream.range(1, VALIDATORS_COUNT + 1)
            .mapToObj(i -> tempDir.resolve("validator_" + i).toFile())
            .toArray(File[]::new);
    assertThat(tempDir.toFile().listFiles()).containsExactlyInAnyOrder(subDirectories);

    for (int i = 0; i < subDirectories.length; i++) {
      assertKeyStoreFilesExist(subDirectories[i].toPath(), i + 1);
    }
  }

  private void assertKeyStoreFilesExist(final Path parentDir, final int suffix) {
    final Path keystore1File = parentDir.resolve("validator_keystore.json");
    final Path keystore2File = parentDir.resolve("withdrawal_keystore.json");

    assertThat(parentDir.toFile().listFiles())
        .containsExactlyInAnyOrder(keystore1File.toFile(), keystore2File.toFile());

    assertThat(
            KeyStore.validatePassword("testpassword", KeyStoreLoader.loadFromFile(keystore1File)))
        .isTrue();
    assertThat(
            KeyStore.validatePassword("testpassword", KeyStoreLoader.loadFromFile(keystore2File)))
        .isTrue();
  }
}
