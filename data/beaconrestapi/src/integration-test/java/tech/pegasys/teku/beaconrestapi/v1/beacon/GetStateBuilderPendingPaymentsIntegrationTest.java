/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateBuilderPendingPayments;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetStateBuilderPendingPaymentsIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetBuilderPendingPaymentsJsonAndSsz() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    createBlocksAtSlots(10);

    final Response jsonResponse = get("head");

    assertThat(jsonResponse.code()).isEqualTo(SC_OK);
    assertThat(jsonResponse.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.GLOAS.lowerCaseName());
    final JsonNode node = JsonTestUtil.parseAsJsonNode(jsonResponse.body().string());
    assertThat(node.get("version").asText()).isEqualTo("gloas");
    assertThat(node.get("execution_optimistic").asBoolean()).isFalse();
    assertThat(node.get("finalized").asBoolean()).isFalse();
    assertThat(node.get("data").size())
        .isEqualTo(2 * spec.getGenesisSpecConfig().getSlotsPerEpoch());

    final SszVectorSchema<BuilderPendingPayment, ?> schema =
        SchemaDefinitionsGloas.required(
                spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions())
            .getBuilderPendingPaymentsSchema();
    final SszVector<BuilderPendingPayment> jsonPayments =
        JsonUtil.parse(node.get("data").toString(), schema.getJsonTypeDefinition());

    final Response sszResponse =
        getResponse(
            GetStateBuilderPendingPayments.ROUTE.replace("{state_id}", "head"), OCTET_STREAM);

    assertThat(sszResponse.code()).isEqualTo(SC_OK);
    assertThat(sszResponse.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(SpecMilestone.GLOAS.lowerCaseName());
    assertThat(Bytes.wrap(sszResponse.body().bytes())).isEqualTo(jsonPayments.sszSerialize());
  }

  @Test
  public void shouldReturnBadRequestBeforeGloas() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.FULU);
    createBlocksAtSlots(10);

    final Response response = get("head");

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string())
        .contains("prior to GLOAS and does not contain builder pending payments");
  }

  public Response get(final String stateId) throws IOException {
    return getResponse(GetStateBuilderPendingPayments.ROUTE.replace("{state_id}", stateId));
  }
}
