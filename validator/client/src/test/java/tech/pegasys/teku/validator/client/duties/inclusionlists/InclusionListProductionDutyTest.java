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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.SpecMilestone.HEZE;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.duties.attestations.BatchAttestationSendingStrategy;

@TestSpecContext(milestone = {HEZE})
class InclusionListProductionDutyTest {

  private static final String TYPE = "inclusion_list";
  private static final UInt64 SLOT = UInt64.valueOf(42);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private SchemaDefinitionsHeze schemaDefinitions;
  private ForkInfo fork;

  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final ValidatorDutyMetrics validatorDutyMetrics =
      spy(ValidatorDutyMetrics.create(new StubMetricsSystem()));

  private InclusionListProductionDuty duty;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    schemaDefinitions = SchemaDefinitionsHeze.required(spec.getGenesisSchemaDefinitions());
    fork = dataStructureUtil.randomForkInfo();
    duty =
        new InclusionListProductionDuty(
            spec,
            SLOT,
            forkProvider,
            new BatchAttestationSendingStrategy<>(validatorApiChannel::sendSignedInclusionLists),
            validatorDutyMetrics,
            validatorApiChannel);
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(validatorApiChannel.sendSignedInclusionLists(any()))
        .thenReturn(completedFuture(Collections.emptyList()));
  }

  @TestTemplate
  void shouldDoNothingWhenNoValidatorsAdded() {
    assertThat(performDuty()).isEqualTo(DutyResult.NOOP);
    verifyNoInteractions(validatorApiChannel);
  }

  @TestTemplate
  void shouldSignAndSendInclusionListWithTransactions() {
    final Validator validator = createValidator();
    final InclusionList inclusionList = inclusionList(1, transaction(Bytes.of(1)));
    final BLSSignature signature = dataStructureUtil.randomSignature();
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList)));
    when(validator.getSigner().signInclusionList(inclusionList, fork))
        .thenReturn(completedFuture(signature));

    final DutyResult result = performDuty();

    assertThat(result.getSuccessCount()).isOne();
    verify(validatorApiChannel)
        .sendSignedInclusionLists(List.of(signedInclusionList(inclusionList, signature)));
  }

  @TestTemplate
  void shouldNotSignOrSendInclusionListWithoutTransactions() {
    final Validator validator = createValidator();
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList(1))));

    assertThat(performDuty()).isEqualTo(DutyResult.NOOP);
    verify(validator.getSigner(), never()).signInclusionList(any(), any());
    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
  }

  @TestTemplate
  void shouldNotSignOrSendInclusionListWithOnlyEmptyTransactions() {
    final Validator validator = createValidator();
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList(1, transaction(Bytes.EMPTY)))));

    assertThat(performDuty()).isEqualTo(DutyResult.NOOP);
    verify(validator.getSigner(), never()).signInclusionList(any(), any());
    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
  }

  @TestTemplate
  void shouldOnlySendInclusionListsWithTransactions() {
    final Validator emptyListValidator = createValidator();
    final Validator validator = createValidator();
    final InclusionList inclusionList = inclusionList(2, transaction(Bytes.of(1)));
    final BLSSignature signature = dataStructureUtil.randomSignature();
    duty.addValidator(emptyListValidator, 1);
    duty.addValidator(validator, 2);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList(1))));
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.valueOf(2)))
        .thenReturn(completedFuture(Optional.of(inclusionList)));
    when(validator.getSigner().signInclusionList(inclusionList, fork))
        .thenReturn(completedFuture(signature));

    final DutyResult result = performDuty();

    assertThat(result.getSuccessCount()).isOne();
    verify(emptyListValidator.getSigner(), never()).signInclusionList(any(), any());
    verify(validatorApiChannel)
        .sendSignedInclusionLists(List.of(signedInclusionList(inclusionList, signature)));
  }

  @TestTemplate
  void shouldFailWhenInclusionListIsUnavailable() {
    final Validator validator = createValidator();
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.empty()));

    final DutyResult result = performDuty();

    assertThat(result.getFailureCount()).isOne();
    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
  }

  @TestTemplate
  void shouldHaveInclusionListProductionType() {
    assertThat(duty.getType()).isEqualTo(DutyType.INCLUSION_LIST_PRODUCTION);
  }

  @TestTemplate
  void shouldNotRecordMetricsWhenNoValidatorsAdded() {
    performDuty();

    verifyNoInteractions(validatorDutyMetrics, forkProvider);
  }

  @TestTemplate
  void shouldRecordCreateAndSignMetricsAndLogSuccess() {
    final Validator validator = createValidator();
    final InclusionList inclusionList = inclusionList(1, transaction(Bytes.of(1)));
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList)));
    when(validator.getSigner().signInclusionList(inclusionList, fork))
        .thenReturn(completedFuture(dataStructureUtil.randomSignature()));

    performAndReportDuty();

    verify(forkProvider).getForkInfo(SLOT);
    verify(validatorDutyMetrics)
        .record(any(), eq(duty), eq(ValidatorDutyMetricsSteps.CREATE_TOTAL));
    verify(validatorDutyMetrics).record(any(), eq(duty), eq(ValidatorDutyMetricsSteps.SIGN));
    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 1, Set.of(inclusionList.getDependentRoot()), Optional.empty());
    verifyNoMoreInteractions(validatorLogger);
  }

  @TestTemplate
  void shouldRequestInclusionListForEachValidatorIndexAndSendInSingleBatch() {
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final InclusionList inclusionList1 = inclusionList(11, transaction(Bytes.of(1)));
    final InclusionList inclusionList2 = inclusionList(22, transaction(Bytes.of(2)));
    final BLSSignature signature1 = dataStructureUtil.randomSignature();
    final BLSSignature signature2 = dataStructureUtil.randomSignature();
    duty.addValidator(validator1, 11);
    duty.addValidator(validator2, 22);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.valueOf(11)))
        .thenReturn(completedFuture(Optional.of(inclusionList1)));
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.valueOf(22)))
        .thenReturn(completedFuture(Optional.of(inclusionList2)));
    when(validator1.getSigner().signInclusionList(inclusionList1, fork))
        .thenReturn(completedFuture(signature1));
    when(validator2.getSigner().signInclusionList(inclusionList2, fork))
        .thenReturn(completedFuture(signature2));

    final DutyResult result = performDuty();

    assertThat(result.getSuccessCount()).isEqualTo(2);
    verify(validatorApiChannel, times(1)).sendSignedInclusionLists(any());
    verify(validatorApiChannel)
        .sendSignedInclusionLists(
            List.of(
                signedInclusionList(inclusionList1, signature1),
                signedInclusionList(inclusionList2, signature2)));
  }

  @TestTemplate
  void shouldFailWhenCreatingInclusionListFails() {
    final Validator validator = createValidator();
    final RuntimeException error = new RuntimeException("Unable to create inclusion list");
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(SafeFuture.failedFuture(error));

    performAndReportDuty();

    verify(validator.getSigner(), never()).signInclusionList(any(), any());
    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
    verify(validatorLogger)
        .dutyFailed(TYPE, SLOT, Set.of(validator.getPublicKey().toAbbreviatedString()), error);
    verifyNoMoreInteractions(validatorLogger);
  }

  @TestTemplate
  void shouldFailWhenSigningInclusionListFails() {
    final Validator validator = createValidator();
    final InclusionList inclusionList = inclusionList(1, transaction(Bytes.of(1)));
    final RuntimeException error = new RuntimeException("Signer unavailable");
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList)));
    when(validator.getSigner().signInclusionList(inclusionList, fork))
        .thenReturn(SafeFuture.failedFuture(error));

    performAndReportDuty();

    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
    verify(validatorLogger)
        .dutyFailed(TYPE, SLOT, Set.of(validator.getPublicKey().toAbbreviatedString()), error);
    verifyNoMoreInteractions(validatorLogger);
  }

  @TestTemplate
  void shouldFailWhenInclusionListIsForDifferentSlot() {
    final Validator validator = createValidator();
    final InclusionList inclusionList =
        schemaDefinitions
            .getInclusionListSchema()
            .create(
                SLOT.increment(),
                UInt64.ONE,
                dataStructureUtil.randomBytes32(),
                List.of(transaction(Bytes.of(1))));
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList)));

    performAndReportDuty();

    verify(validator.getSigner(), never()).signInclusionList(any(), any());
    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(SLOT),
            eq(Set.of(validator.getPublicKey().toAbbreviatedString())),
            argThat(error -> error instanceof IllegalArgumentException));
    verifyNoMoreInteractions(validatorLogger);
  }

  @TestTemplate
  void shouldReportPartialFailureWhenBeaconNodeRejectsSomeInclusionLists() {
    final Validator validator1 = createValidator();
    final Validator validator2 = createValidator();
    final InclusionList inclusionList1 = inclusionList(11, transaction(Bytes.of(1)));
    final InclusionList inclusionList2 = inclusionList(22, transaction(Bytes.of(2)));
    duty.addValidator(validator1, 11);
    duty.addValidator(validator2, 22);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.valueOf(11)))
        .thenReturn(completedFuture(Optional.of(inclusionList1)));
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.valueOf(22)))
        .thenReturn(completedFuture(Optional.of(inclusionList2)));
    when(validator1.getSigner().signInclusionList(inclusionList1, fork))
        .thenReturn(completedFuture(dataStructureUtil.randomSignature()));
    when(validator2.getSigner().signInclusionList(inclusionList2, fork))
        .thenReturn(completedFuture(dataStructureUtil.randomSignature()));
    when(validatorApiChannel.sendSignedInclusionLists(any()))
        .thenReturn(completedFuture(List.of(new SubmitDataError(UInt64.ZERO, "API Rejected"))));

    performAndReportDuty();

    verify(validatorLogger)
        .dutyCompleted(TYPE, SLOT, 1, Set.of(inclusionList2.getDependentRoot()), Optional.empty());
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(SLOT),
            eq(Set.of(validator1.getPublicKey().toAbbreviatedString())),
            argThat(error -> error.getMessage().equals("API Rejected")));
    verifyNoMoreInteractions(validatorLogger);
  }

  @TestTemplate
  void shouldFailDutyWhenForkInfoIsUnavailable() {
    final Validator validator = createValidator();
    final RuntimeException error = new RuntimeException("No fork info");
    duty.addValidator(validator, 1);
    when(forkProvider.getForkInfo(SLOT)).thenReturn(SafeFuture.failedFuture(error));

    assertThatSafeFuture(duty.performDuty()).isCompletedExceptionallyWith(error);
    verifyNoInteractions(validatorApiChannel);
  }

  private void performAndReportDuty() {
    performDuty().report(TYPE, SLOT, validatorLogger);
  }

  private DutyResult performDuty() {
    return safeJoin(duty.performDuty());
  }

  private Validator createValidator() {
    return new Validator(
        dataStructureUtil.randomPublicKey(), mock(Signer.class), new FileBackedGraffitiProvider());
  }

  private Transaction transaction(final Bytes bytes) {
    return schemaDefinitions.getExecutionPayloadSchema().getTransactionSchema().fromBytes(bytes);
  }

  private InclusionList inclusionList(final int validatorIndex, final Transaction... transactions) {
    return schemaDefinitions
        .getInclusionListSchema()
        .create(
            SLOT,
            UInt64.valueOf(validatorIndex),
            dataStructureUtil.randomBytes32(),
            List.of(transactions));
  }

  private SignedInclusionList signedInclusionList(
      final InclusionList inclusionList, final BLSSignature signature) {
    return schemaDefinitions.getSignedInclusionListSchema().create(inclusionList, signature);
  }
}
