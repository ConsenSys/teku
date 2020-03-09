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

package tech.pegasys.artemis.cli.deposit;

import java.io.File;
import picocli.CommandLine.Option;

public class ValidatorKeysPasswordGroup implements EncryptedKeysPasswordGroup {
  @Option(
      names = {"--validator-password:file"},
      paramLabel = "<FILE>",
      required = true,
      description = "Read password from the file to encrypt the validator keys.")
  File passwordFile;

  @Option(
      names = {"--validator-password:env"},
      paramLabel = "<ENV_VAR>",
      required = true,
      description = "Read password from environment variable to encrypt the validator keys.")
  String passwordEnv;

  @Option(
      names = {"--validator-password"},
      paramLabel = "<PASSWORD>",
      description = "Read password in interactive mode to encrypt validator keys.",
      required = true,
      interactive = true)
  String password;

  public ValidatorKeysPasswordGroup() {}

  public ValidatorKeysPasswordGroup(
      final File passwordFile, final String passwordEnv, final String password) {
    this.passwordFile = passwordFile;
    this.passwordEnv = passwordEnv;
    this.password = password;
  }

  @Override
  public File readPasswordFromFile() {
    return passwordFile;
  }

  public void setPasswordFile(final File passwordFile) {
    this.passwordFile = passwordFile;
  }

  @Override
  public String readPasswordFromEnvironmentVariable() {
    return passwordEnv;
  }

  public void setPasswordEnv(final String passwordEnv) {
    this.passwordEnv = passwordEnv;
  }

  @Override
  public String readPasswordInteractively() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }
}
