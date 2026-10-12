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

package tech.pegasys.teku.validator.coordinator.duties;

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuty;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.heze.util.InclusionListUtil;

class InclusionListDutiesGeneratorTest {

  private static final int VALIDATOR_COUNT = 64;

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final BeaconState state =
      new GenesisStateBuilder().spec(spec).addMockValidators(VALIDATOR_COUNT).build();
  private final InclusionListDutiesGenerator generator = new InclusionListDutiesGenerator(spec);

  @Test
  void shouldCreateDutiesMatchingSpecCommitteeAssignment() {
    final UInt64 epoch = UInt64.ZERO;
    final IntList validatorIndices =
        new IntArrayList(IntStream.range(0, VALIDATOR_COUNT).toArray());

    final InclusionListDuties duties =
        generator.getInclusionListDutiesFromIndicesAndState(state, epoch, validatorIndices, false);

    final List<InclusionListDuty> expected = new ArrayList<>();
    for (final int validatorIndex : validatorIndices) {
      inclusionListCommitteeAssignment(epoch, validatorIndex)
          .ifPresent(
              slot ->
                  expected.add(
                      new InclusionListDuty(
                          spec.getValidatorPubKey(state, UInt64.valueOf(validatorIndex))
                              .orElseThrow(),
                          UInt64.valueOf(validatorIndex),
                          slot)));
    }
    assertThat(expected).isNotEmpty();
    assertThat(duties.duties()).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldNotCreateDutiesForUnknownValidators() {
    final InclusionListDuties duties =
        generator.getInclusionListDutiesFromIndicesAndState(
            state, UInt64.ZERO, IntList.of(VALIDATOR_COUNT + 10), false);

    assertThat(duties.duties()).isEmpty();
  }

  @Test
  void shouldUsePreviousDutyDependentRootForCurrentEpoch() {
    final InclusionListDuties duties =
        generator.getInclusionListDutiesFromIndicesAndState(
            state, UInt64.ZERO, IntList.of(0), true);

    assertThat(duties.dependentRoot())
        .isEqualTo(
            spec.atEpoch(UInt64.ZERO).getBeaconStateUtil().getPreviousDutyDependentRoot(state));
    assertThat(duties.executionOptimistic()).isTrue();
  }

  @Test
  void shouldUseCurrentDutyDependentRootForNextEpoch() {
    final InclusionListDuties duties =
        generator.getInclusionListDutiesFromIndicesAndState(
            state, UInt64.ONE, IntList.of(0), false);

    assertThat(duties.dependentRoot())
        .isEqualTo(
            spec.atEpoch(UInt64.ONE).getBeaconStateUtil().getCurrentDutyDependentRoot(state));
    assertThat(duties.executionOptimistic()).isFalse();
  }

  /** The spec's get_inclusion_list_committee_assignment: the first slot of the epoch. */
  private Optional<UInt64> inclusionListCommitteeAssignment(
      final UInt64 epoch, final int validatorIndex) {
    final InclusionListUtil inclusionListUtil =
        spec.atEpoch(epoch).getInclusionListUtil().orElseThrow();
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(epoch);
    for (int i = 0; i < spec.getSlotsPerEpoch(startSlot); i++) {
      final UInt64 slot = startSlot.plus(i);
      if (inclusionListUtil.getInclusionListCommittee(state, slot).contains(validatorIndex)) {
        return Optional.of(slot);
      }
    }
    return Optional.empty();
  }
}
