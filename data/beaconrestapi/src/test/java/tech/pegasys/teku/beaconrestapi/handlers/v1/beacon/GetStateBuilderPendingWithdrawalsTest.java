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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseSszFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetStateBuilderPendingWithdrawalsTest
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  private SszListSchema<BuilderPendingWithdrawal, ?> withdrawalsSchema;

  @BeforeEach
  public void setup() {
    initialise(SpecMilestone.GLOAS);
    genesis();
    setHandler(new GetStateBuilderPendingWithdrawals(chainDataProvider, schemaDefinitionCache));
    request.setPathParameter("state_id", "head");
    withdrawalsSchema =
        SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
            .getBuilderPendingWithdrawalsSchema();
  }

  @Test
  void shouldReturnBuilderPendingWithdrawalsFromHeadState() throws JsonProcessingException {
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseHeaders("Eth-Consensus-Version"))
        .isEqualTo(SpecMilestone.GLOAS.lowerCaseName());
    @SuppressWarnings("unchecked")
    final ObjectAndMetaData<SszList<BuilderPendingWithdrawal>> response =
        (ObjectAndMetaData<SszList<BuilderPendingWithdrawal>>) request.getResponseBody();
    assertThat(response.getMilestone()).isEqualTo(SpecMilestone.GLOAS);
    assertThat(response.getData()).isEmpty();
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle406() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_ACCEPTABLE);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200() throws Exception {
    final BuilderPendingWithdrawal withdrawal = dataStructureUtil.randomBuilderPendingWithdrawal();
    final SszList<BuilderPendingWithdrawal> withdrawals = withdrawalsSchema.of(withdrawal);
    final ObjectAndMetaData<SszList<BuilderPendingWithdrawal>> responseData =
        new ObjectAndMetaData<>(withdrawals, SpecMilestone.GLOAS, false, true, false);

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);

    final JsonNode node = JsonTestUtil.parseAsJsonNode(data);
    assertThat(node.get("version").asText()).isEqualTo("gloas");
    assertThat(node.get("execution_optimistic").asBoolean()).isFalse();
    assertThat(node.get("finalized").asBoolean()).isFalse();
    final JsonNode item = node.get("data").get(0);
    assertThat(item.get("amount").asText()).isEqualTo(withdrawal.getAmount().toString());
    assertThat(item.get("builder_index").asText())
        .isEqualTo(withdrawal.getBuilderIndex().toString());
    assertThat(item.has("fee_recipient")).isTrue();
    assertThat(
            JsonUtil.parse(node.get("data").toString(), withdrawalsSchema.getJsonTypeDefinition()))
        .isEqualTo(withdrawals);
  }

  @Test
  void metadata_shouldHandle200OctetStream() throws IOException {
    final SszList<BuilderPendingWithdrawal> withdrawals =
        withdrawalsSchema.of(
            dataStructureUtil.randomBuilderPendingWithdrawal(),
            dataStructureUtil.randomBuilderPendingWithdrawal());
    final ObjectAndMetaData<SszList<BuilderPendingWithdrawal>> responseData =
        new ObjectAndMetaData<>(withdrawals, SpecMilestone.GLOAS, false, true, false);

    final byte[] data = getResponseSszFromMetadata(handler, SC_OK, responseData);

    assertThat(Bytes.of(data)).isEqualTo(withdrawals.sszSerialize());
  }
}
