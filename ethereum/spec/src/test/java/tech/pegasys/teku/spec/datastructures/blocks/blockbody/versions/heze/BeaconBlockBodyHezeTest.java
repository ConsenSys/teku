/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.heze;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconBlockBodyHezeTest {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconBlockBodySchemaHezeImpl schema =
      (BeaconBlockBodySchemaHezeImpl) spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema();

  @Test
  void schema_shouldLeaveGapsForRemovedFields() {
    assertThat(schema.getActiveFields())
        .isEqualTo(
            new boolean[] {
              true, false, true, true, true, true, false, true, true, true, true, true, true
            });
    assertThat(schema.getFieldIndex(BlockBodyFields.ETH1_DATA)).isEqualTo(-1);
    assertThat(schema.getFieldIndex(BlockBodyFields.DEPOSITS)).isEqualTo(-1);
    assertThat(schema.getTreePosition(schema.getFieldIndex(BlockBodyFields.GRAFFITI))).isEqualTo(2);
    assertThat(schema.getTreePosition(schema.getFieldIndex(BlockBodyFields.VOLUNTARY_EXITS)))
        .isEqualTo(7);
  }

  @Test
  void builder_shouldNotRequireRemovedFields() {
    final BeaconBlockBody template = dataStructureUtil.randomBeaconBlockBody();

    final BeaconBlockBody body =
        safeJoin(
            schema.createBlockBody(
                builder -> {
                  builder
                      .randaoReveal(template.getRandaoReveal())
                      .graffiti(template.getGraffiti())
                      .proposerSlashings(template.getProposerSlashings())
                      .attesterSlashings(template.getAttesterSlashings())
                      .attestations(template.getAttestations())
                      .voluntaryExits(template.getVoluntaryExits())
                      .syncAggregate(template.getOptionalSyncAggregate().orElseThrow())
                      .blsToExecutionChanges(
                          template.getOptionalBlsToExecutionChanges().orElseThrow())
                      .signedExecutionPayloadBid(
                          template.getOptionalSignedExecutionPayloadBid().orElseThrow())
                      .payloadAttestations(template.getOptionalPayloadAttestations().orElseThrow())
                      .parentExecutionRequests(
                          template.getOptionalParentExecutionRequests().orElseThrow());
                  return SafeFuture.COMPLETE;
                }));

    assertThat(body.hashTreeRoot()).isEqualTo(template.hashTreeRoot());
  }

  @Test
  void removedFieldGetters_shouldReturnEmptyValuesAndLogErrors() {
    final BeaconBlockBody body = dataStructureUtil.randomBeaconBlockBody();

    try (final LogCaptor logCaptor = LogCaptor.forClass(Eip8015RemovedFields.class)) {
      assertThat(body.getEth1Data()).isEqualTo(Eip8015RemovedFields.EMPTY_ETH1_DATA);
      assertThat(body.getDeposits()).isEmpty();

      assertThat(logCaptor.getErrorLogs())
          .containsExactly(
              "Accessed eth1_data, which was removed in Heze (EIP-8015)",
              "Accessed deposits, which was removed in Heze (EIP-8015)");
    }
  }

  @Test
  void sszRoundTrip_shouldPreserveBody() {
    final BeaconBlockBody body = dataStructureUtil.randomBeaconBlockBody();

    final BeaconBlockBody deserialized = schema.sszDeserialize(body.sszSerialize());

    assertThat(deserialized).isInstanceOf(BeaconBlockBodyHezeImpl.class);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(body.hashTreeRoot());
  }
}
