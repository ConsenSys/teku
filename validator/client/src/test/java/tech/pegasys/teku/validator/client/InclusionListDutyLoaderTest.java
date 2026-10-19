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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyFactory;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.inclusionlists.InclusionListProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class InclusionListDutyLoaderTest {

  private static final UInt64 EPOCH = UInt64.valueOf(3);

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Validator validator1 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private final Validator validator2 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private final int validator1Index = 19;
  private final int validator2Index = 23;
  private final IntSet validatorIndices = IntSet.of(validator1Index, validator2Index);
  private final OwnedValidators validators =
      new OwnedValidators(
          Map.of(validator1.getPublicKey(), validator1, validator2.getPublicKey(), validator2));
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  @SuppressWarnings("unchecked")
  private final DutyFactory<InclusionListProductionDuty, Duty> dutyFactory =
      mock(DutyFactory.class);

  private final Map<UInt64, InclusionListProductionDuty> createdDuties = new HashMap<>();
  private final List<Bytes32> requestedDependentRoots = new ArrayList<>();

  private final InclusionListDutyLoader dutyLoader =
      new InclusionListDutyLoader(
          validatorApiChannel,
          dependentRoot -> {
            requestedDependentRoots.add(dependentRoot);
            return new SlotBasedScheduledDuties<>(
                dutyFactory, dependentRoot, duty -> SafeFuture.completedFuture(DutyResult.NOOP));
          },
          validators,
          validatorIndexProvider);

  @BeforeEach
  void setUp() {
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(validatorIndices));
    when(dutyFactory.createProductionDuty(any(), any()))
        .thenAnswer(
            invocation -> {
              final UInt64 slot = invocation.getArgument(0);
              final InclusionListProductionDuty duty = mock(InclusionListProductionDuty.class);
              createdDuties.put(slot, duty);
              return duty;
            });
  }

  @Test
  void shouldHaveInclusionListDutyType() {
    assertThat(dutyLoader.getDutyType()).isEqualTo("INCLUSION_LIST");
  }

  @Test
  void shouldRequestDutiesForEpochAndKnownValidatorIndices() {
    withDuties(dataStructureUtil.randomBytes32());

    loadDuties();

    verify(validatorApiChannel).getInclusionListDuties(EPOCH, validatorIndices);
  }

  @Test
  void shouldScheduleProductionDutyForEachOwnedValidator() {
    final UInt64 slot1 = UInt64.valueOf(25);
    final UInt64 slot2 = UInt64.valueOf(26);
    withDuties(
        dataStructureUtil.randomBytes32(),
        duty(validator1.getPublicKey(), validator1Index, slot1),
        duty(validator2.getPublicKey(), validator2Index, slot2));

    final SlotBasedScheduledDuties<?, ?> scheduledDuties = loadDuties();

    assertThat(scheduledDuties.countDuties()).isEqualTo(2);
    assertThat(createdDuties).containsOnlyKeys(slot1, slot2);
    verify(dutyFactory).createProductionDuty(slot1, validator1);
    verify(dutyFactory).createProductionDuty(slot2, validator2);
    verify(createdDuties.get(slot1)).addValidator(validator1, validator1Index);
    verify(createdDuties.get(slot2)).addValidator(validator2, validator2Index);
  }

  @Test
  void shouldAddAllValidatorsWithDutyInSameSlotToSingleProductionDuty() {
    final UInt64 slot = UInt64.valueOf(25);
    withDuties(
        dataStructureUtil.randomBytes32(),
        duty(validator1.getPublicKey(), validator1Index, slot),
        duty(validator2.getPublicKey(), validator2Index, slot));

    final SlotBasedScheduledDuties<?, ?> scheduledDuties = loadDuties();

    assertThat(scheduledDuties.countDuties()).isOne();
    verify(dutyFactory, times(1)).createProductionDuty(any(), any());
    final InclusionListProductionDuty productionDuty = createdDuties.get(slot);
    verify(productionDuty).addValidator(validator1, validator1Index);
    verify(productionDuty).addValidator(validator2, validator2Index);
  }

  @Test
  void shouldIgnoreDutiesForValidatorsThatAreNotOwned() {
    final UInt64 ownedSlot = UInt64.valueOf(25);
    final UInt64 unownedSlot = UInt64.valueOf(27);
    withDuties(
        dataStructureUtil.randomBytes32(),
        duty(validator1.getPublicKey(), validator1Index, ownedSlot),
        duty(dataStructureUtil.randomPublicKey(), 99, unownedSlot));

    final SlotBasedScheduledDuties<?, ?> scheduledDuties = loadDuties();

    assertThat(scheduledDuties.countDuties()).isOne();
    assertThat(createdDuties).containsOnlyKeys(ownedSlot);
    final InclusionListProductionDuty productionDuty = createdDuties.get(ownedSlot);
    verify(productionDuty).addValidator(validator1, validator1Index);
    verify(productionDuty, times(1)).addValidator(any(), anyInt());
    verify(productionDuty, times(0)).addValidator(any(), eq(99));
  }

  @Test
  void shouldNotScheduleProductionDutiesWhenNoDutiesAreForOwnedValidators() {
    withDuties(
        dataStructureUtil.randomBytes32(),
        duty(dataStructureUtil.randomPublicKey(), 99, UInt64.valueOf(25)));

    final SlotBasedScheduledDuties<?, ?> scheduledDuties = loadDuties();

    assertThat(scheduledDuties.countDuties()).isZero();
    verifyNoInteractions(dutyFactory);
  }

  @Test
  void shouldReturnEmptyScheduledDutiesWhenBeaconNodeReturnsNoDuties() {
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    withDuties(dependentRoot);

    final SlotBasedScheduledDuties<?, ?> scheduledDuties = loadDuties();

    assertThat(scheduledDuties.countDuties()).isZero();
    assertThat(scheduledDuties.getDependentRoot()).isEqualTo(dependentRoot);
    verifyNoInteractions(dutyFactory);
  }

  @Test
  void shouldCreateScheduledDutiesWithDependentRootFromResponse() {
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    withDuties(dependentRoot, duty(validator1.getPublicKey(), validator1Index, UInt64.ONE));

    final SlotBasedScheduledDuties<?, ?> scheduledDuties = loadDuties();

    assertThat(requestedDependentRoots).containsExactly(dependentRoot);
    assertThat(scheduledDuties.getDependentRoot()).isEqualTo(dependentRoot);
    assertThat(scheduledDuties.requiresRecalculation(dependentRoot)).isFalse();
    assertThat(scheduledDuties.requiresRecalculation(dataStructureUtil.randomBytes32())).isTrue();
  }

  @Test
  void shouldNotRequestDutiesWhenNoValidatorIndicesAreKnown() {
    when(validatorIndexProvider.getValidatorIndices())
        .thenReturn(SafeFuture.completedFuture(IntSet.of()));

    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(EPOCH);

    assertThatSafeFuture(result).isCompletedWithEmptyOptional();
    verifyNoInteractions(validatorApiChannel);
    assertThat(requestedDependentRoots).isEmpty();
  }

  @Test
  void shouldFailWithNodeDataUnavailableWhenBeaconNodeCannotProvideDuties() {
    when(validatorApiChannel.getInclusionListDuties(EPOCH, validatorIndices))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(EPOCH);

    assertThatSafeFuture(result).isCompletedExceptionallyWith(NodeDataUnavailableException.class);
    assertThat(requestedDependentRoots).isEmpty();
  }

  @Test
  void shouldPropagateFailureFromValidatorApiChannel() {
    final RuntimeException error = new RuntimeException("Beacon node unavailable");
    when(validatorApiChannel.getInclusionListDuties(EPOCH, validatorIndices))
        .thenReturn(SafeFuture.failedFuture(error));

    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(EPOCH);

    assertThatSafeFuture(result).isCompletedExceptionallyWith(error);
    assertThat(requestedDependentRoots).isEmpty();
  }

  @Test
  void shouldPropagateFailureFromValidatorIndexProvider() {
    final RuntimeException error = new RuntimeException("Unable to load validator indices");
    when(validatorIndexProvider.getValidatorIndices()).thenReturn(SafeFuture.failedFuture(error));

    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(EPOCH);

    assertThatSafeFuture(result).isCompletedExceptionallyWith(error);
    verifyNoInteractions(validatorApiChannel);
  }

  private void withDuties(final Bytes32 dependentRoot, final InclusionListDuty... duties) {
    when(validatorApiChannel.getInclusionListDuties(EPOCH, validatorIndices))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new InclusionListDuties(false, dependentRoot, List.of(duties)))));
  }

  private static InclusionListDuty duty(
      final BLSPublicKey publicKey, final int validatorIndex, final UInt64 slot) {
    return new InclusionListDuty(publicKey, UInt64.valueOf(validatorIndex), slot);
  }

  private SlotBasedScheduledDuties<?, ?> loadDuties() {
    final SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>> result =
        dutyLoader.loadDutiesForEpoch(EPOCH);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
    return safeJoin(result).orElseThrow();
  }
}
