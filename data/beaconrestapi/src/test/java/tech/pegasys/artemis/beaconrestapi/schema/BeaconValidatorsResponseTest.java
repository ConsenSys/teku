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

package tech.pegasys.artemis.beaconrestapi.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.PAGE_TOKEN_DEFAULT;

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;

class BeaconValidatorsResponseTest {

  @Test
  public void validatorsResponseShouldConformToDefaults() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(99);
    BeaconValidatorsResponse validators = new BeaconValidatorsResponse(beaconState.getValidators());
    assertThat(validators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(validators.validatorList.size())
        .isLessThanOrEqualTo(RestApiConstants.PAGE_SIZE_DEFAULT);
    assertThat(validators.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT + 1);
  }

  @Test
  public void activeValidatorsResponseShouldConformToDefaults() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(98);
    BeaconValidatorsResponse validators =
        new BeaconValidatorsResponse(beaconState.getActiveValidators());
    assertThat(validators.getTotalSize()).isEqualTo(beaconState.getActiveValidators().size());
    assertThat(validators.validatorList.size())
        .isLessThanOrEqualTo(RestApiConstants.PAGE_SIZE_DEFAULT);
    assertThat(validators.getNextPageToken()).isLessThanOrEqualTo(PAGE_TOKEN_DEFAULT + 1);
  }

  @Test
  public void suppliedPageSizeParamIsUsed() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final int suppliedPageSizeParam = 10;

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(
            beaconState.getValidators(), suppliedPageSizeParam, PAGE_TOKEN_DEFAULT);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.validatorList.size()).isEqualTo(suppliedPageSizeParam);
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(PAGE_TOKEN_DEFAULT + 1);
  }

  @Test
  public void suppliedPageParamsAreUsed() {
    BeaconState beaconState = DataStructureUtil.randomBeaconState(97);
    final int suppliedPageSizeParam = 10;
    final int suppliedPageTokenParam = 1;

    BeaconValidatorsResponse beaconValidators =
        new BeaconValidatorsResponse(
            beaconState.getValidators(), suppliedPageSizeParam, suppliedPageTokenParam);
    assertThat(beaconValidators.getTotalSize()).isEqualTo(beaconState.getValidators().size());
    assertThat(beaconValidators.validatorList.size()).isEqualTo(suppliedPageSizeParam);
    assertThat(beaconValidators.getNextPageToken()).isEqualTo(suppliedPageTokenParam + 1);
  }
}
