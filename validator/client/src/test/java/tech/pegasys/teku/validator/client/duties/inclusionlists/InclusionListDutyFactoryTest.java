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

package tech.pegasys.teku.validator.client.duties.inclusionlists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.SpecMilestone.HEZE;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;

@TestSpecContext(milestone = {HEZE})
class InclusionListDutyFactoryTest {

  private static final UInt64 SLOT = UInt64.valueOf(42);

  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorDutyMetrics validatorDutyMetrics =
      ValidatorDutyMetrics.create(new StubMetricsSystem());

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private SchemaDefinitionsHeze schemaDefinitions;
  private InclusionListDutyFactory dutyFactory;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    schemaDefinitions = SchemaDefinitionsHeze.required(spec.getGenesisSchemaDefinitions());
    dutyFactory =
        new InclusionListDutyFactory(spec, forkProvider, validatorApiChannel, validatorDutyMetrics);
  }

  @TestTemplate
  void shouldReportInclusionListProductionType() {
    assertThat(dutyFactory.getProductionType()).isEqualTo("inclusion_list");
  }

  @TestTemplate
  void shouldNotHaveAggregationType() {
    assertThat(dutyFactory.getAggregationType()).isEmpty();
  }

  @TestTemplate
  void shouldNotSupportAggregationDuties() {
    assertThatThrownBy(() -> dutyFactory.createAggregationDuty(SLOT, createValidator()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @TestTemplate
  void shouldCreateInclusionListProductionDuty() {
    final InclusionListProductionDuty duty =
        dutyFactory.createProductionDuty(SLOT, createValidator());

    assertThat(duty.getType()).isEqualTo(DutyType.INCLUSION_LIST_PRODUCTION);
    verifyNoInteractions(validatorApiChannel, forkProvider);
  }

  @TestTemplate
  void shouldCreateDistinctDutiesForEachCall() {
    final Validator validator = createValidator();

    assertThat(dutyFactory.createProductionDuty(SLOT, validator))
        .isNotSameAs(dutyFactory.createProductionDuty(SLOT, validator));
  }

  @TestTemplate
  void shouldCreateDutyWithoutValidatorsSoItIsNoopUntilValidatorsAdded() {
    final InclusionListProductionDuty duty =
        dutyFactory.createProductionDuty(SLOT, createValidator());

    assertThat(safeJoin(duty.performDuty())).isEqualTo(DutyResult.NOOP);
    verifyNoInteractions(validatorApiChannel, forkProvider);
  }

  @TestTemplate
  void shouldCreateDutyForSlotThatRequestsSignsAndSendsInclusionListsViaApiChannel() {
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    final Validator validator = createValidator();
    final UInt64 validatorIndex = UInt64.valueOf(7);
    final InclusionList inclusionList =
        schemaDefinitions
            .getInclusionListSchema()
            .create(
                SLOT,
                validatorIndex,
                dataStructureUtil.randomBytes32(),
                List.of(
                    schemaDefinitions
                        .getExecutionPayloadSchema()
                        .getTransactionSchema()
                        .fromBytes(Bytes.of(1, 2, 3))));
    final BLSSignature signature = dataStructureUtil.randomSignature();
    when(forkProvider.getForkInfo(SLOT)).thenReturn(completedFuture(forkInfo));
    when(validatorApiChannel.createInclusionList(SLOT, validatorIndex))
        .thenReturn(completedFuture(Optional.of(inclusionList)));
    when(validator.getSigner().signInclusionList(inclusionList, forkInfo))
        .thenReturn(completedFuture(signature));
    when(validatorApiChannel.sendSignedInclusionLists(any()))
        .thenReturn(completedFuture(List.of()));

    final InclusionListProductionDuty duty = dutyFactory.createProductionDuty(SLOT, validator);
    duty.addValidator(validator, validatorIndex.intValue());
    final DutyResult result = safeJoin(duty.performDuty());

    assertThat(result.getSuccessCount()).isOne();
    verify(forkProvider).getForkInfo(SLOT);
    verify(validatorApiChannel).createInclusionList(SLOT, validatorIndex);
    verify(validatorApiChannel)
        .sendSignedInclusionLists(
            List.of(
                schemaDefinitions.getSignedInclusionListSchema().create(inclusionList, signature)));
  }

  private Validator createValidator() {
    return new Validator(
        dataStructureUtil.randomPublicKey(), mock(Signer.class), new FileBackedGraffitiProvider());
  }
}
