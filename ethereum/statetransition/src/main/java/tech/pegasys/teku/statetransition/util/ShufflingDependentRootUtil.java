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

package tech.pegasys.teku.statetransition.util;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public final class ShufflingDependentRootUtil {

  private static final UInt64 ONE = UInt64.ONE;

  private ShufflingDependentRootUtil() {}

  public static Optional<Bytes32> getShufflingDependentRoot(
      final Spec spec,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 blockRoot,
      final UInt64 proposalSlot) {
    final UInt64 proposalEpoch = spec.computeEpochAtSlot(proposalSlot);
    final UInt64 minSeedLookahead =
        UInt64.valueOf(spec.getSpecConfig(proposalEpoch).getMinSeedLookahead());
    final UInt64 dependentSlot =
        proposalEpoch.isLessThanOrEqualTo(minSeedLookahead)
            ? UInt64.ZERO
            : spec.computeStartSlotAtEpoch(proposalEpoch.minus(minSeedLookahead)).minus(ONE);
    return forkChoiceStrategy.getAncestor(blockRoot, dependentSlot);
  }

  public static Optional<UInt64> getShufflingDependentSlotForEpoch(
      final Spec spec, final UInt64 proposalEpoch) {
    final UInt64 minSeedLookahead =
        UInt64.valueOf(spec.getSpecConfig(proposalEpoch).getMinSeedLookahead());
    if (proposalEpoch.isLessThanOrEqualTo(minSeedLookahead)) {
      return Optional.empty();
    }
    return Optional.of(
        spec.computeStartSlotAtEpoch(proposalEpoch.minus(minSeedLookahead)).minus(ONE));
  }

  /** Resolves older dependent blocks which may no longer be retained by fork choice. */
  public static Bytes32 getShufflingDependentRoot(
      final Spec spec, final BeaconState state, final UInt64 slot) {
    final UInt64 dependentSlot =
        getShufflingDependentSlotForEpoch(spec, spec.computeEpochAtSlot(slot)).orElse(UInt64.ZERO);
    return dependentSlot.equals(state.getSlot())
        ? BeaconBlockHeader.fromState(state).getRoot()
        : spec.getBlockRootAtSlot(state, dependentSlot);
  }

  /**
   * The inclusion list store key for a proposal or payload in {@code slot}: the previous slot and
   * the shuffling dependent root of {@code blockRoot}'s chain for that slot's epoch, as in {@code
   * record_payload_inclusion_list_satisfaction} and {@code prepare_execution_payload}. Fork choice
   * is used when it knows the dependent block, else {@code state}, which must be on {@code
   * blockRoot}'s chain with block roots covering the dependent slot.
   */
  public static SlotAndBlockRoot getInclusionListKey(
      final Spec spec,
      final Optional<? extends ReadOnlyForkChoiceStrategy> maybeForkChoiceStrategy,
      final Bytes32 blockRoot,
      final BeaconState state,
      final UInt64 slot) {
    final UInt64 inclusionListSlot = slot.minusMinZero(ONE);
    return maybeForkChoiceStrategy
        .flatMap(
            forkChoiceStrategy ->
                getShufflingDependentRoot(spec, forkChoiceStrategy, blockRoot, inclusionListSlot))
        .map(dependentRoot -> new SlotAndBlockRoot(inclusionListSlot, dependentRoot))
        .orElseGet(() -> getInclusionListKey(spec, state, slot));
  }

  /**
   * The inclusion list store key for a proposal or payload in {@code slot}, resolved from {@code
   * state} alone. See {@link #getInclusionListKey(Spec, Optional, Bytes32, BeaconState, UInt64)}.
   */
  public static SlotAndBlockRoot getInclusionListKey(
      final Spec spec, final BeaconState state, final UInt64 slot) {
    final UInt64 inclusionListSlot = slot.minusMinZero(ONE);
    return new SlotAndBlockRoot(
        inclusionListSlot, getShufflingDependentRoot(spec, state, inclusionListSlot));
  }
}
