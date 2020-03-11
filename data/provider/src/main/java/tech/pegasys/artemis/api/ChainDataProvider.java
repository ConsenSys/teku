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

package tech.pegasys.artemis.api;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.api.schema.AttestationData;
import tech.pegasys.artemis.api.schema.BLSPubKey;
import tech.pegasys.artemis.api.schema.BLSSignature;
import tech.pegasys.artemis.api.schema.BeaconChainHead;
import tech.pegasys.artemis.api.schema.BeaconHead;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.api.schema.Committee;
import tech.pegasys.artemis.api.schema.SignedBeaconBlock;
import tech.pegasys.artemis.api.schema.Validator;
import tech.pegasys.artemis.api.schema.ValidatorDuties;
import tech.pegasys.artemis.api.schema.ValidatorsRequest;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.async.SafeFuture;

public class ChainDataProvider {
  private final CombinedChainDataClient combinedChainDataClient;

  private final ChainStorageClient chainStorageClient;

  public ChainDataProvider(
      final ChainStorageClient chainStorageClient,
      final CombinedChainDataClient combinedChainDataClient) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.chainStorageClient = chainStorageClient;
  }

  public Optional<UnsignedLong> getGenesisTime() {
    if (!isStoreAvailable()) {
      return Optional.empty();
    }
    return Optional.ofNullable(chainStorageClient.getGenesisTime());
  }

  public Optional<BeaconHead> getBeaconHead() {
    if (!isStoreAvailable()) {
      return Optional.empty();
    }

    final Bytes32 headBlockRoot = chainStorageClient.getBestBlockRoot();
    if (headBlockRoot == null) {
      return Optional.empty();
    }

    final Bytes32 headStateRoot = chainStorageClient.getBestBlockRootState().hash_tree_root();
    final BeaconHead result =
        new BeaconHead(chainStorageClient.getBestSlot(), headBlockRoot, headStateRoot);
    return Optional.of(result);
  }

  public SafeFuture<List<Committee>> getCommitteesAtEpoch(UnsignedLong epoch) {
    if (!isStoreAvailable()) {
      return completedFuture(List.of());
    }
    return combinedChainDataClient
        .getCommitteeAssignmentAtEpoch(epoch)
        .thenApply(result -> result.stream().map(Committee::new).collect(Collectors.toList()))
        .exceptionally(err -> List.of());
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockBySlot(UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return completedFuture(Optional.empty());
    }
    return combinedChainDataClient
        .getBlockBySlot(slot)
        .thenApply(block -> block.map(SignedBeaconBlock::new));
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient != null && combinedChainDataClient.isStoreAvailable();
  }

  public Optional<Bytes32> getBestBlockRoot() {
    return combinedChainDataClient.getBestBlockRoot();
  }

  ChainStorageClient getChainStorageClient() {
    return chainStorageClient;
  }

  CombinedChainDataClient getCombinedChainDataClient() {
    return combinedChainDataClient;
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(Bytes32 blockParam) {
    if (!isStoreAvailable()) {
      return completedFuture(Optional.empty());
    }
    return combinedChainDataClient
        .getBlockByBlockRoot(blockParam)
        .thenApply(block -> block.map(SignedBeaconBlock::new));
  }

  public SafeFuture<Optional<BeaconState>> getStateByBlockRoot(Bytes32 blockRoot) {
    if (!isStoreAvailable()) {
      return completedFuture(Optional.empty());
    }
    return combinedChainDataClient
        .getStateByBlockRoot(blockRoot)
        .thenApply(state -> state.map(BeaconState::new))
        .exceptionally(err -> Optional.empty());
  }

  public SafeFuture<Optional<BeaconState>> getStateAtSlot(UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return completedFuture(Optional.empty());
    }
    final Bytes32 headBlockRoot = combinedChainDataClient.getBestBlockRoot().orElse(null);
    return combinedChainDataClient
        .getStateAtSlot(slot, headBlockRoot)
        .thenApply(state -> state.map(BeaconState::new))
        .exceptionally(err -> Optional.empty());
  }

  public SafeFuture<Optional<Bytes32>> getHashTreeRootAtSlot(UnsignedLong slot) {
    if (!isStoreAvailable()) {
      return completedFuture(Optional.empty());
    }
    final Bytes32 headBlockRoot = combinedChainDataClient.getBestBlockRoot().orElse(null);
    return combinedChainDataClient
        .getStateAtSlot(slot, headBlockRoot)
        .thenApply(state -> Optional.of(state.get().hash_tree_root()))
        .exceptionally(err -> Optional.empty());
  }

  public Optional<Attestation> getUnsignedAttestationAtSlot(
      UnsignedLong slot, Integer committeeIndex) {
    if (!isStoreAvailable()) {
      return Optional.empty();
    }
    if (isFinalized(slot)) {
      throw new IllegalArgumentException(
          String.format("Slot %s is finalized, no attestation will be created.", slot.toString()));
    }
    Optional<BeaconBlock> block = chainStorageClient.getBlockBySlot(slot);
    if (block.isEmpty()) {
      return Optional.empty();
    }

    tech.pegasys.artemis.datastructures.state.BeaconState state =
        chainStorageClient.getBestBlockRootState();
    int committeeCount = get_committee_count_at_slot(state, slot).intValue();
    if (committeeIndex < 0 || committeeIndex >= committeeCount) {
      throw new IllegalArgumentException(
          "Invalid committee index provided - expected between 0 and " + (committeeCount - 1));
    }

    tech.pegasys.artemis.datastructures.operations.AttestationData internalAttestation =
        AttestationUtil.getGenericAttestationData(state, block.get());
    AttestationData data = new AttestationData(internalAttestation);
    Bitlist aggregationBits = AttestationUtil.getAggregationBits(committeeCount, committeeIndex);
    Attestation attestation = new Attestation(aggregationBits, data, BLSSignature.empty());
    return Optional.of(attestation);
  }

  public boolean isFinalized(SignedBeaconBlock signedBeaconBlock) {
    return combinedChainDataClient.isFinalized(signedBeaconBlock.message.slot);
  }

  public boolean isFinalized(UnsignedLong slot) {
    return combinedChainDataClient.isFinalized(slot);
  }

  public List<ValidatorDuties> getValidatorDuties(final ValidatorsRequest validatorsRequest) {

    UnsignedLong epoch = validatorsRequest.epoch;
    Optional<Bytes32> optionalBlockRoot = getBestBlockRoot();
    if (optionalBlockRoot.isEmpty()) {
      return List.of();
    }
    //    UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);
    // TODO deal with future
    //    BeaconState state = combinedChainDataClient.getStateAtSlot(slot, optionalBlockRoot.get());
    //    List<Validator> validators = state.validators;
    List<Validator> validators = List.of();

    List<ValidatorDuties> dutiesList = new ArrayList<>();
    for (BLSPubKey pubKey : validatorsRequest.pubkeys) {
      dutiesList.add(getValidatorDuties(validators, epoch, pubKey));
    }
    return dutiesList;
  }

  private ValidatorDuties getValidatorDuties(
      final List<Validator> validators, final UnsignedLong epoch, final BLSPubKey pubKey) {
    int validatorIndex = getValidatorIndex(validators, pubKey);

    // TODO deal with future
    if (epoch.compareTo(UnsignedLong.ZERO) < 0) {
      return ValidatorDuties.empty();
    }
    ; // TODO delete this
    //    List<Committee> committees = combinedChainDataClient
    //        .getCommitteeAssignmentAtEpoch(epoch)
    //        .thenApply(result -> result.stream().map(Committee::new).collect(Collectors.toList()))
    //        .exceptionally(err -> List.of());
    List<Committee> committees = List.of();

    return new ValidatorDuties(
        getCommitteeIndex(committees, validatorIndex), pubKey, validatorIndex);
  }

  @VisibleForTesting
  protected static int getValidatorIndex(
      final List<Validator> validators, final BLSPubKey publicKey) {
    Optional<Validator> optionalValidator =
        validators.stream().filter(v -> publicKey.equals(v.pubkey)).findFirst();
    if (optionalValidator.isPresent()) {
      return validators.indexOf(optionalValidator.get());
    } else {
      return -1;
    }
  }

  @VisibleForTesting
  protected int getCommitteeIndex(List<Committee> committees, int validatorIndex) {
    Optional<Committee> matchingCommittee =
        committees.stream()
            .filter(committee -> committee.committee.contains(validatorIndex))
            .findFirst();
    if (matchingCommittee.isPresent()) {
      return committees.indexOf(matchingCommittee.get());
    } else {
      return -1;
    }
  }

  public Optional<BeaconChainHead> getHeadState() {
    return combinedChainDataClient.getHeadStateFromStore().map(BeaconChainHead::new);
  }
}
