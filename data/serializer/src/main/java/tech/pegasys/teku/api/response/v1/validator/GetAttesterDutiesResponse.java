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

package tech.pegasys.teku.api.response.v1.validator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class GetAttesterDutiesResponse {
  @JsonProperty("dependent_root")
  public final Bytes32 dependentRoot;

  public final List<AttesterDuty> data;

  @JsonCreator
  public GetAttesterDutiesResponse(
      @JsonProperty("dependent_root") final Bytes32 dependentRoot,
      @JsonProperty("data") final List<AttesterDuty> data) {
    this.dependentRoot = dependentRoot;
    this.data = data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GetAttesterDutiesResponse that = (GetAttesterDutiesResponse) o;
    return Objects.equals(dependentRoot, that.dependentRoot) && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dependentRoot, data);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dependentRoot", dependentRoot)
        .add("data", data)
        .toString();
  }
}
