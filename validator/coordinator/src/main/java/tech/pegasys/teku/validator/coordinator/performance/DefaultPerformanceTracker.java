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

package tech.pegasys.teku.validator.coordinator.performance;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.config.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;

public class DefaultPerformanceTracker implements PerformanceTracker {

  @VisibleForTesting
  final NavigableMap<UInt64, Set<SignedBeaconBlock>> sentBlocksByEpoch = new TreeMap<>();

  @VisibleForTesting
  final NavigableMap<UInt64, Set<Attestation>> sentAttestationsByEpoch = new TreeMap<>();

  @VisibleForTesting
  static final UInt64 BLOCK_PERFORMANCE_EVALUATION_INTERVAL = UInt64.valueOf(2); // epochs

  static final UInt64 ATTESTATION_INCLUSION_RANGE = UInt64.valueOf(2);

  private final CombinedChainDataClient combinedChainDataClient;
  private final StatusLogger statusLogger;
  private Optional<UInt64> nodeStartEpoch = Optional.empty();

  public DefaultPerformanceTracker(
      CombinedChainDataClient combinedChainDataClient, StatusLogger statusLogger) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.statusLogger = statusLogger;
  }

  @Override
  public void start(UInt64 nodeStartSlot) {
    this.nodeStartEpoch = Optional.of(compute_epoch_at_slot(nodeStartSlot));
  }

  @Override
  public void onSlot(UInt64 slot) {
    if (nodeStartEpoch.isEmpty()) {
      return;
    }

    if (slot.mod(Constants.SLOTS_PER_EPOCH).isGreaterThan(UInt64.ZERO)) {
      return;
    }

    UInt64 currentEpoch = compute_epoch_at_slot(slot);
    // Output attestation performance information for current epoch - 2 since attestations can be
    // included in both the epoch they were produced in or in the one following.
    if (currentEpoch.isGreaterThanOrEqualTo(
        nodeStartEpoch.get().plus(ATTESTATION_INCLUSION_RANGE))) {
      UInt64 analyzedEpoch = currentEpoch.minus(ATTESTATION_INCLUSION_RANGE);
      statusLogger.performance(
          getAttestationPerformanceForEpoch(currentEpoch, analyzedEpoch).toString());
      sentAttestationsByEpoch.headMap(analyzedEpoch, true).clear();
    }

    // Output block performance information for the past BLOCK_PERFORMANCE_INTERVAL epochs
    if (currentEpoch.isGreaterThanOrEqualTo(BLOCK_PERFORMANCE_EVALUATION_INTERVAL)) {
      if (currentEpoch.mod(BLOCK_PERFORMANCE_EVALUATION_INTERVAL).equals(UInt64.ZERO)) {
        UInt64 oldestAnalyzedEpoch = currentEpoch.minus(BLOCK_PERFORMANCE_EVALUATION_INTERVAL);
        statusLogger.performance(
            getBlockPerformanceForEpochs(oldestAnalyzedEpoch, currentEpoch).toString());
        sentBlocksByEpoch.headMap(oldestAnalyzedEpoch, true).clear();
      }
    }
  }

  private BlockPerformance getBlockPerformanceForEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    List<SignedBeaconBlock> sentBlocks =
        sentBlocksByEpoch.subMap(startEpochInclusive, true, endEpochExclusive, false).values()
            .stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    long numberOfIncludedBlocks =
        sentBlocks.stream()
            .filter(
                sentBlock ->
                    combinedChainDataClient
                        .getBlockAtSlotExact(sentBlock.getSlot())
                        .join()
                        .map(block -> block.equals(sentBlock))
                        .orElse(false))
            .count();

    return new BlockPerformance((int) numberOfIncludedBlocks, sentBlocks.size());
  }

  private AttestationPerformance getAttestationPerformanceForEpoch(
      UInt64 currentEpoch, UInt64 analyzedEpoch) {
    checkArgument(
            analyzedEpoch.isLessThanOrEqualTo(currentEpoch.minus(ATTESTATION_INCLUSION_RANGE)),
            "Epoch to analyze attestation performance must be at least 2 epochs less than the current epoch");
    // Attestations can be included in either the epoch they were produced in or in
    // the following epoch. Thus, the most recent epoch for which we can evaluate attestation
    // performance is current epoch - 2.
    UInt64 analysisRangeEndEpoch = analyzedEpoch.plus(ATTESTATION_INCLUSION_RANGE);

    // Get included attestations for the given epochs in a map from slot to attestations
    // included in block.
    Map<UInt64, List<Attestation>> attestationsIncludedOnChain =
            getAttestationsIncludedInEpochs(analyzedEpoch, analysisRangeEndEpoch);

    // Get sent attestations in range
    Set<Attestation> sentAttestations =
            sentAttestationsByEpoch.getOrDefault(analyzedEpoch, new HashSet<>());
    BeaconState state = combinedChainDataClient.getBestState().orElseThrow();

    int correctTargetCount = 0;
    int correctHeadBlockCount = 0;
    List<Integer> inclusionDistances = new ArrayList<>();

    // Pre-process attestations included on chain to group them by
    // data hash to inclusion slot to aggregation bitlist
    Map<Bytes32, NavigableMap<UInt64, Bitlist>> slotAndBitlistsByAttestationDataHash = new HashMap<>();
    for (UInt64 slot : attestationsIncludedOnChain.keySet()) {
      for (Attestation attestation : attestationsIncludedOnChain.get(slot)) {
        Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
        NavigableMap<UInt64, Bitlist> slotToBitlists = slotAndBitlistsByAttestationDataHash
                .computeIfAbsent(attestationDataHash, __ -> new TreeMap<>());
        slotToBitlists.put(slot, attestation.getAggregation_bits());
      }
    }

    for (Attestation sentAttestation : sentAttestations) {
      Bytes32 sentAttestationDataHash = sentAttestation.getData().hash_tree_root();
      UInt64 sentAttestationSlot = sentAttestation.getData().getSlot();
      if (!slotAndBitlistsByAttestationDataHash.containsKey(sentAttestationDataHash)) continue;
      List<SlotAndBitlist> slotAndBitlists = slotAndBitlistsByAttestationDataHash.get(sentAttestationDataHash);
      for (SlotAndBitlist slotAndBitlist : slotAndBitlists) {
        if (slotAndBitlist.bitlist.isSuperSetOf(sentAttestation.getAggregation_bits())) {
          inclusionDistances.add(slotAndBitlist.slot.minus(sentAttestationSlot).intValue());
          break;
        }
      }

      // Check if the attestation had correct target
      Bytes32 attestationTargetRoot = sentAttestation.getData().getTarget().getRoot();
      if (attestationTargetRoot.equals(get_block_root(state, analyzedEpoch))) {
        correctTargetCount++;

        // Check if the attestation had correct head block root
        Bytes32 attestationHeadBlockRoot = sentAttestation.getData().getBeacon_block_root();
        if (attestationHeadBlockRoot.equals(get_block_root_at_slot(state, sentAttestationSlot))) {
          correctHeadBlockCount++;
        }
      }
    }

    IntSummaryStatistics inclusionDistanceStatistics =
            inclusionDistances.stream().collect(Collectors.summarizingInt(Integer::intValue));

    // IntSummaryStatistics returns Integer.MIN and MAX when the summarizend integer list is empty.
    return sentAttestations.size() > 0
            ? new AttestationPerformance(
            sentAttestations.size(),
            (int) inclusionDistanceStatistics.getCount(),
            inclusionDistanceStatistics.getMax(),
            inclusionDistanceStatistics.getMin(),
            inclusionDistanceStatistics.getAverage(),
            correctTargetCount,
            correctHeadBlockCount)
            : AttestationPerformance.empty();
  }

  private Set<BeaconBlock> getBlocksInEpochs(UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    UInt64 epochStartSlot = compute_start_slot_at_epoch(startEpochInclusive);
    UInt64 endEpochStartSlot = compute_start_slot_at_epoch(endEpochExclusive);

    Set<BeaconBlock> blocksInEpoch = new HashSet<>();
    for (UInt64 currSlot = epochStartSlot;
        currSlot.isLessThan(endEpochStartSlot);
        currSlot = currSlot.increment()) {
      combinedChainDataClient
          .getBlockAtSlotExact(currSlot)
          .join()
          .ifPresent(signedBlock -> blocksInEpoch.add(signedBlock.getMessage()));
    }

    return blocksInEpoch;
  }

  private Map<UInt64, List<Attestation>> getAttestationsIncludedInEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    return getBlocksInEpochs(startEpochInclusive, endEpochExclusive).stream()
        .collect(
            Collectors.toMap(
                BeaconBlock::getSlot, block -> block.getBody().getAttestations().asList()));
  }

  @Override
  public void saveSentAttestation(Attestation attestation) {
    UInt64 epoch = compute_epoch_at_slot(attestation.getData().getSlot());
    Set<Attestation> attestationsInEpoch =
        sentAttestationsByEpoch.computeIfAbsent(epoch, __ -> new HashSet<>());
    attestationsInEpoch.add(attestation);
  }

  @Override
  public void saveSentBlock(SignedBeaconBlock block) {
    UInt64 epoch = compute_epoch_at_slot(block.getSlot());
    Set<SignedBeaconBlock> blocksInEpoch =
        sentBlocksByEpoch.computeIfAbsent(epoch, __ -> new HashSet<>());
    blocksInEpoch.add(block);
  }

  static long getPercentage(final long numerator, final long denominator) {
    return (long) (numerator * 100.0 / denominator + 0.5);
  }

  private static class SlotAndBitlist {
    protected final UInt64 slot;
    protected final Bitlist bitlist;

    public SlotAndBitlist(UInt64 slot, Bitlist bitlist) {
      this.slot = slot;
      this.bitlist = bitlist;
    }
  }
}
