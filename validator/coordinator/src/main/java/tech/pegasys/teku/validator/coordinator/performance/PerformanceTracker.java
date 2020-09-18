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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

import java.util.ArrayList;
import java.util.Collection;
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

public class PerformanceTracker implements SlotEventsChannel {

  @VisibleForTesting
  final NavigableMap<UInt64, Set<SignedBeaconBlock>> sentBlocksByEpoch = new TreeMap<>();
  @VisibleForTesting
  final NavigableMap<UInt64, Set<Attestation>> sentAttestationsByEpoch = new TreeMap<>();

  @VisibleForTesting
  static final UInt64 BLOCK_PERFORMANCE_EVALUATION_INTERVAL = UInt64.valueOf(2); // epochs

  private final RecentChainData recentChainData;
  private final StatusLogger statusLogger;

  public PerformanceTracker(RecentChainData recentChainData, StatusLogger statusLogger) {
    this.recentChainData = recentChainData;
    this.statusLogger = statusLogger;
  }

  @Override
  public void onSlot(UInt64 slot) {
    UInt64 currentEpoch = compute_epoch_at_slot(slot);
    if (!compute_start_slot_at_epoch(currentEpoch).equals(slot)) {
      return;
    }

    // Output attestation performance information for current epoch - 2 since attestations can be
    // included in both the epoch they were produced in or in the one following.
    if (currentEpoch.isGreaterThanOrEqualTo(UInt64.valueOf(2))) {
      statusLogger.performance(
          getAttestationPerformanceForEpoch(currentEpoch, currentEpoch.minus(UInt64.valueOf(2)))
              .toString());
    }

    // Output block performance information for the past BLOCK_PERFORMANCE_INTERVAL epochs
    if (currentEpoch.isGreaterThanOrEqualTo(BLOCK_PERFORMANCE_EVALUATION_INTERVAL)) {
      if (currentEpoch.mod(BLOCK_PERFORMANCE_EVALUATION_INTERVAL).equals(UInt64.ZERO)) {
        statusLogger.performance(
            getBlockPerformanceForEpochs(
                    currentEpoch.minus(BLOCK_PERFORMANCE_EVALUATION_INTERVAL), currentEpoch)
                .toString());
      }
    }

    UInt64 epoch = currentEpoch.minus(BLOCK_PERFORMANCE_EVALUATION_INTERVAL)
            .min(currentEpoch.minus(UInt64.valueOf(2)));
    clearReduntantSavedSentObjects(epoch);
  }

  private BlockPerformance getBlockPerformanceForEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    Set<BeaconBlock> blockInEpoch = getBlocksInEpochs(startEpochInclusive, endEpochExclusive);
    List<SignedBeaconBlock> sentBlocks =
        sentBlocksByEpoch.subMap(startEpochInclusive, true, endEpochExclusive, false).values()
            .stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    long numberOfIncludedBlocks =
        sentBlocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .filter(blockInEpoch::contains)
            .count();

    return new BlockPerformance((int) numberOfIncludedBlocks, sentBlocks.size());
  }

  private AttestationPerformance getAttestationPerformanceForEpoch(
      UInt64 currentEpoch, UInt64 analyzedEpoch) {
    checkArgument(
        analyzedEpoch.isLessThanOrEqualTo(currentEpoch.minus(UInt64.valueOf(2))),
        "Epoch to analyze attestation performance must be at least 2 epochs less than the current epoch");
    // Attestations can be included in either the epoch they were produced in or in
    // the following epoch. Thus, the most recent epoch for which we can evaluate attestation
    // performance is current epoch - 2.
    UInt64 analysisRangeEndEpoch = analyzedEpoch.plus(UInt64.valueOf(2));

    // Get included attestations for the given epochs in a map from slot to attestations
    // included in block.
    Map<UInt64, List<Attestation>> attestations =
        getAttestationsIncludedInEpochs(analyzedEpoch, analysisRangeEndEpoch);

    // Get sent attestations in range
    Set<Attestation> sentAttestations =
        sentAttestationsByEpoch.getOrDefault(analyzedEpoch, new HashSet<>());
    UInt64 analyzedEpochStartSlot = compute_start_slot_at_epoch(analyzedEpoch);
    UInt64 rangeEndSlot = compute_start_slot_at_epoch(analysisRangeEndEpoch);
    BeaconState state = recentChainData.getBestState().orElseThrow();

    int correctTargetCount = 0;
    int correctHeadBlockCount = 0;
    List<Integer> inclusionDistances = new ArrayList<>();

    for (Attestation sentAttestation : sentAttestations) {
      // Check if the sent attestation is included in any block in the appropriate range.
      // Appropriate range being: [ attestation_production_epoch, attestation_production_epoch + 1 ]
      UInt64 attestationSlot = sentAttestation.getData().getSlot();
      for (UInt64 currSlot = analyzedEpochStartSlot;
          currSlot.isLessThan(rangeEndSlot);
          currSlot = currSlot.increment()) {
        if (attestations.containsKey(currSlot)) {
          if (checkIfAttestationIsIncludedInList(sentAttestation, attestations.get(currSlot))) {
            inclusionDistances.add(currSlot.minus(attestationSlot).intValue());
          }
        }
      }

      // Check if the attestation had correct target
      Bytes32 attestationTargetRoot = sentAttestation.getData().getTarget().getRoot();
      if (attestationTargetRoot.equals(get_block_root(state, analyzedEpoch))) {
        correctTargetCount++;

        // Check if the attestation had correct head block root
        Bytes32 attestationHeadBlockRoot = sentAttestation.getData().getBeacon_block_root();
        if (attestationHeadBlockRoot.equals(get_block_root_at_slot(state, attestationSlot))) {
          correctHeadBlockCount++;
        }
      }
    }

    IntSummaryStatistics inclusionDistanceStatistics =
        inclusionDistances.stream().collect(Collectors.summarizingInt(Integer::intValue));

    return new AttestationPerformance(
        sentAttestations.size(),
        (int) inclusionDistanceStatistics.getCount(),
        inclusionDistanceStatistics.getMax(),
        inclusionDistanceStatistics.getMin(),
        inclusionDistanceStatistics.getAverage(),
        correctTargetCount,
        correctHeadBlockCount);
  }

  private boolean checkIfAttestationIsIncludedInList(
      Attestation sentAttestation, List<Attestation> aggregateAttestations) {
    for (Attestation aggregateAttestation : aggregateAttestations) {
      if (checkIfAttestationIsIncludedIn(sentAttestation, aggregateAttestation)) {
        return true;
      }
    }
    return false;
  }

  private boolean checkIfAttestationIsIncludedIn(
      Attestation sentAttestation, Attestation aggregateAttestation) {
    return sentAttestation.getData().equals(aggregateAttestation.getData())
        && aggregateAttestation
            .getAggregation_bits()
            .isSuperSetOf(sentAttestation.getAggregation_bits());
  }

  private Set<BeaconBlock> getBlocksInEpochs(UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    UInt64 epochStartSlot = compute_start_slot_at_epoch(startEpochInclusive);
    UInt64 endEpochStartSlot = compute_start_slot_at_epoch(endEpochExclusive);

    List<Bytes32> blockRootsInEpoch = new ArrayList<>();
    for (UInt64 currSlot = epochStartSlot;
        currSlot.isLessThan(endEpochStartSlot);
        currSlot = currSlot.increment()) {
      recentChainData.getBlockRootBySlot(currSlot).ifPresent(blockRootsInEpoch::add);
    }

    return blockRootsInEpoch.stream()
        .map(recentChainData::retrieveBlockByRoot)
        .map(SafeFuture::join)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  private Map<UInt64, List<Attestation>> getAttestationsIncludedInEpochs(
      UInt64 startEpochInclusive, UInt64 endEpochExclusive) {
    return getBlocksInEpochs(startEpochInclusive, endEpochExclusive).stream()
        .collect(
            Collectors.toMap(
                BeaconBlock::getSlot, block -> block.getBody().getAttestations().asList()));
  }

  private void clearReduntantSavedSentObjects(UInt64 epoch) {
    sentAttestationsByEpoch.headMap(epoch, true).clear();
    sentBlocksByEpoch.headMap(epoch, true).clear();
  }

  public void saveSentAttestation(Attestation attestation) {
    UInt64 epoch = compute_epoch_at_slot(attestation.getData().getSlot());
    Set<Attestation> attestationsInEpoch =
        sentAttestationsByEpoch.computeIfAbsent(epoch, __ -> new HashSet<>());
    attestationsInEpoch.add(attestation);
  }

  public void saveSentBlock(SignedBeaconBlock block) {
    UInt64 epoch = compute_epoch_at_slot(block.getSlot());
    Set<SignedBeaconBlock> blocksInEpoch =
        sentBlocksByEpoch.computeIfAbsent(epoch, __ -> new HashSet<>());
    blocksInEpoch.add(block);
  }

  static long getPercentage(final long numerator, final long denominator) {
    return (long) (numerator * 100.0 / denominator + 0.5);
  }
}
