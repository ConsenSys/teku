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

package tech.pegasys.teku.beaconrestapi.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostVoluntaryExit;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostVoluntaryExitIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    Response response =
        post(PostVoluntaryExit.ROUTE, jsonProvider.objectToJSON("{\"foo\": \"bar\"}"));
    assertThat(response.code()).isEqualTo(400);
  }

  @Test
  public void shouldReturnServerErrorWhenUnexpectedErrorHappens() throws Exception {
    final tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit signedVoluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit();

    final SignedVoluntaryExit schemaExit =
        new tech.pegasys.teku.api.schema.SignedVoluntaryExit(signedVoluntaryExit);

    doThrow(new RuntimeException()).when(voluntaryExitPool).add(signedVoluntaryExit);

    Response response = post(PostVoluntaryExit.ROUTE, jsonProvider.objectToJSON(schemaExit));
    assertThat(response.code()).isEqualTo(500);
  }

  @Test
  public void shouldReturnSuccessWhenRequestBodyIsValid() throws Exception {
    final tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit signedVoluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit();

    final SignedVoluntaryExit schemaExit =
        new tech.pegasys.teku.api.schema.SignedVoluntaryExit(signedVoluntaryExit);

    when(voluntaryExitPool.add(signedVoluntaryExit))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    Response response = post(PostVoluntaryExit.ROUTE, jsonProvider.objectToJSON(schemaExit));

    verify(voluntaryExitPool).add(signedVoluntaryExit);

    assertThat(response.code()).isEqualTo(200);
  }
}
