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

package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

public class CipherParam extends Param {
  private Bytes initializationVector;

  @JsonCreator
  public CipherParam(
      @JsonProperty(value = "iv", required = true) final Bytes initializationVector) {
    this.initializationVector = initializationVector;
  }

  @JsonProperty(value = "iv")
  public Bytes getInitializationVector() {
    return initializationVector;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("iv", initializationVector).toString();
  }
}
