package tech.pegasys.teku.validator.coordinator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZImmutableCollection;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

public class PerformanceTracker implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Map<UInt64, List<SignedBeaconBlock>> sentBlocksByEpoch = new HashMap<>();
  private final Map<UInt64, List<Attestation>> sentAttestationsByEpoch = new HashMap<>();

  private final RecentChainData recentChainData;

  public PerformanceTracker(RecentChainData recentChainData) {
    this.recentChainData = recentChainData;
  }

  @Override
  public void onSlot(UInt64 slot) {
    UInt64 currentEpoch = compute_epoch_at_slot(slot);
    if (!compute_start_slot_at_epoch().equals(slot)) {
      return;
    }

    // Output performance information for the past epoch
    outputPerformanceInformation(currentEpoch.decrement());
  }

  private void outputPerformanceInformation(final UInt64 epoch) {
    outputBlockPerformanceInfo(epoch);
    outputAttestationPerformanceInfo(epoch);
  }

  private void outputBlockPerformanceInfo(UInt64 epoch) {
    List<BeaconBlock> blockInEpoch = getBlocksInEpoch(epoch);
    List<SignedBeaconBlock> sentBlocks = sentBlocksByEpoch.get(epoch);
    long numberOfSentBlocks = sentBlocks.size();
    long numberOfIncludedSentBlocks = sentBlocks
            .stream()
            .map(SignedBeaconBlock::getMessage)
            .filter(blockInEpoch::contains)
            .count();

    LOG.info("Number of sent blocks: {} | Number of sent blocks included on chain: {}.",
            numberOfSentBlocks, numberOfIncludedSentBlocks);
    LOG.info("Block inclusion at: {}%", getPercentage(numberOfIncludedSentBlocks, numberOfSentBlocks));
  }


  private void outputAttestationPerformanceInfo(final UInt64 epoch) {
    UInt64 previousEpoch = epoch.decrement();

    List<Attestation> allAttestations = new ArrayList<>();
    allAttestations.addAll(getAttestationsInEpoch(epoch));
    allAttestations.addAll(getAttestationsInEpoch(previousEpoch));

    List<Attestation> sentAttestations = sentAttestationsByEpoch.get(previousEpoch);
    long numberOfSentAttestations = sentAttestations.size();
    long numberOfIncludedSentAttestations = sentAttestations
            .stream()
            .filter(a -> checkIfAttestationIsIncludedInList(a, allAttestations))
            .count();

    LOG.info("Number of sent attestations: {} | Number of sent attestations included on chain: {}.",
            numberOfSentAttestations, numberOfIncludedSentAttestations);
    LOG.info("Attestation inclusion at: {}%",
            getPercentage(numberOfIncludedSentAttestations, numberOfSentAttestations));
  }

  private boolean checkIfAttestationIsIncludedInList(Attestation sentAttestation, List<Attestation> aggregateAttestations) {
    for (Attestation aggregateAttestation : aggregateAttestations) {
      if (checkIfAttestationIsIncludedIn(sentAttestation, aggregateAttestation)) {
        return true;
      }
    }
    return false;
  }

  private boolean checkIfAttestationIsIncludedIn(Attestation sentAttestation, Attestation aggregateAttestation) {
    return sentAttestation.getData().equals(aggregateAttestation.getData()) &&
            aggregateAttestation.getAggregation_bits().isSuperSetOf(sentAttestation.getAggregation_bits());
  }

  private List<BeaconBlock> getBlocksInEpoch(UInt64 epoch) {
    UInt64 epochStartSlot = compute_start_slot_at_epoch(epoch);
    UInt64 nextEpochStartSlot = compute_start_slot_at_epoch(epoch.increment());

    List<Bytes32> blockRootsInEpoch = new ArrayList<>();
    for (UInt64 currSlot = epochStartSlot;
         currSlot.isLessThan(nextEpochStartSlot);
         currSlot = currSlot.increment()) {
      recentChainData.getBlockRootBySlot(currSlot).ifPresent(blockRootsInEpoch::add);
    }

    return blockRootsInEpoch.stream()
            .map(recentChainData::retrieveBlockByRoot)
            .map(SafeFuture::join)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
  }

  private List<Attestation> getAttestationsInEpoch(UInt64 epoch) {
    return getBlocksInEpoch(epoch).stream()
            .map(BeaconBlock::getBody)
            .map(BeaconBlockBody::getAttestations)
            .map(SSZImmutableCollection::asList)
            .flatMap(List::stream)
            .collect(Collectors.toList());
  }

  public void saveSentAttestation(Attestation attestation) {
    UInt64 epoch = compute_epoch_at_slot(attestation.getData().getSlot());
    List<Attestation> attestationsInEpoch =
            sentAttestationsByEpoch.computeIfAbsent(epoch, __ -> new ArrayList<>());
    attestationsInEpoch.add(attestation);
  }

  public void saveSentBlock(SignedBeaconBlock block) {
    UInt64 epoch = compute_epoch_at_slot(block.getSlot());
    List<SignedBeaconBlock> blocksInEpoch =
            sentBlocksByEpoch.computeIfAbsent(epoch, __ -> new ArrayList<>());
    blocksInEpoch.add(block);
  }

  private static long getPercentage(final long numerator, final long denominator) {
   return (long)(numerator * 100.0 / denominator + 0.5);
  }
}
