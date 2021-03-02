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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.collections.SszBytes32Vector;
import tech.pegasys.teku.ssz.backing.collections.SszPrimitiveVector;
import tech.pegasys.teku.ssz.backing.collections.SszUInt64List;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class BeaconStateBuilder {
  private final DataStructureUtil dataStructureUtil;
  private final int defaultValidatorCount;
  private final int defaultItemsInSSZLists;

  private UInt64 genesisTime;
  private Bytes32 genesisValidatorsRoot;
  private UInt64 slot;
  private Fork fork;
  private BeaconBlockHeader latestBlockHeader;
  private SszBytes32Vector blockRoots;
  private SszBytes32Vector stateRoots;
  private SSZList<Bytes32> historicalRoots;
  private Eth1Data eth1Data;
  private SszList<Eth1Data> eth1DataVotes;
  private UInt64 eth1DepositIndex;
  private SszList<Validator> validators;
  private SszUInt64List balances;
  private SszBytes32Vector randaoMixes;
  private SszPrimitiveVector<UInt64, SszUInt64> slashings;
  private SszList<PendingAttestation> previousEpochAttestations;
  private SszList<PendingAttestation> currentEpochAttestations;
  private SszBitvector justificationBits;
  private Checkpoint previousJustifiedCheckpoint;
  private Checkpoint currentJustifiedCheckpoint;
  private Checkpoint finalizedCheckpoint;

  private BeaconStateBuilder(
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    this.dataStructureUtil = dataStructureUtil;
    this.defaultValidatorCount = defaultValidatorCount;
    this.defaultItemsInSSZLists = defaultItemsInSSZLists;
    initDefaults();
  }

  public static BeaconStateBuilder create(
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilder(dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconState build() {
    return BeaconState.create(
        genesisTime,
        genesisValidatorsRoot,
        slot,
        fork,
        latestBlockHeader,
        blockRoots,
        stateRoots,
        historicalRoots,
        eth1Data,
        eth1DataVotes,
        eth1DepositIndex,
        validators,
        balances,
        randaoMixes,
        slashings,
        previousEpochAttestations,
        currentEpochAttestations,
        justificationBits,
        previousJustifiedCheckpoint,
        currentJustifiedCheckpoint,
        finalizedCheckpoint);
  }

  private void initDefaults() {
    genesisTime = dataStructureUtil.randomUInt64();
    genesisValidatorsRoot = dataStructureUtil.randomBytes32();
    slot = dataStructureUtil.randomUInt64();
    fork = dataStructureUtil.randomFork();
    latestBlockHeader = dataStructureUtil.randomBeaconBlockHeader();
    blockRoots =
        dataStructureUtil.randomSszBytes32Vector(
            BeaconState.BLOCK_ROOTS_FIELD_SCHEMA.get(), dataStructureUtil::randomBytes32);
    stateRoots =
        dataStructureUtil.randomSszBytes32Vector(
            BeaconState.STATE_ROOTS_FIELD_SCHEMA.get(), dataStructureUtil::randomBytes32);
    historicalRoots =
        dataStructureUtil.randomSSZList(
            Bytes32.class,
            defaultItemsInSSZLists,
            dataStructureUtil.getHistoricalRootsLimit(),
            dataStructureUtil::randomBytes32);
    eth1Data = dataStructureUtil.randomEth1Data();
    eth1DataVotes =
        dataStructureUtil.randomSszList(
            BeaconState.ETH1_DATA_VOTES_FIELD_SCHEMA.get(),
            dataStructureUtil.getEpochsPerEth1VotingPeriod() * dataStructureUtil.getSlotsPerEpoch(),
            dataStructureUtil::randomEth1Data);
    eth1DepositIndex = dataStructureUtil.randomUInt64();
    validators =
        dataStructureUtil.randomSszList(
            BeaconState.VALIDATORS_FIELD_SCHEMA.get(),
            defaultValidatorCount,
            dataStructureUtil::randomValidator);
    balances =
        dataStructureUtil.randomSszUInt64List(
            BeaconState.BALANCES_FIELD_SCHEMA.get(),
            defaultValidatorCount,
            dataStructureUtil::randomUInt64);
    randaoMixes =
        dataStructureUtil.randomSszBytes32Vector(
            BeaconState.RANDAO_MIXES_FIELD_SCHEMA.get(), dataStructureUtil::randomBytes32);
    slashings =
        dataStructureUtil.randomSszPrimitiveVector(
            BeaconState.SLASHINGS_FIELD_SCHEMA.get(), dataStructureUtil::randomUInt64);
    previousEpochAttestations =
        dataStructureUtil.randomSszList(
            BeaconState.PREVIOUS_EPOCH_ATTESTATIONS_FIELD_SCHEMA.get(),
            dataStructureUtil.getMaxAttestations() * dataStructureUtil.getSlotsPerEpoch(),
            dataStructureUtil::randomPendingAttestation);
    currentEpochAttestations =
        dataStructureUtil.randomSszList(
            BeaconState.CURRENT_EPOCH_ATTESTATIONS_FIELD_SCHEMA.get(),
            dataStructureUtil.getMaxAttestations() * dataStructureUtil.getSlotsPerEpoch(),
            dataStructureUtil::randomPendingAttestation);
    justificationBits =
        dataStructureUtil.randomSszBitvector(dataStructureUtil.getJustificationBitsLength());
    previousJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();
    currentJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();
    finalizedCheckpoint = dataStructureUtil.randomCheckpoint();
  }

  public BeaconStateBuilder genesisTime(final UInt64 genesisTime) {
    checkNotNull(genesisTime);
    this.genesisTime = genesisTime;
    return this;
  }

  public BeaconStateBuilder genesisValidatorsRoot(final Bytes32 genesisValidatorsRoot) {
    checkNotNull(genesisValidatorsRoot);
    this.genesisValidatorsRoot = genesisValidatorsRoot;
    return this;
  }

  public BeaconStateBuilder slot(final UInt64 slot) {
    checkNotNull(slot);
    this.slot = slot;
    return this;
  }

  public BeaconStateBuilder setSlotToStartOfEpoch(final UInt64 epoch) {
    checkNotNull(epoch);
    return slot(dataStructureUtil.computeStartSlotAtEpoch(epoch));
  }

  public BeaconStateBuilder fork(final Fork fork) {
    checkNotNull(fork);
    this.fork = fork;
    return this;
  }

  public BeaconStateBuilder latestBlockHeader(final BeaconBlockHeader latestBlockHeader) {
    checkNotNull(latestBlockHeader);
    this.latestBlockHeader = latestBlockHeader;
    return this;
  }

  public BeaconStateBuilder blockRoots(final SszBytes32Vector blockRoots) {
    checkNotNull(blockRoots);
    this.blockRoots = blockRoots;
    return this;
  }

  public BeaconStateBuilder stateRoots(final SszBytes32Vector stateRoots) {
    checkNotNull(stateRoots);
    this.stateRoots = stateRoots;
    return this;
  }

  public BeaconStateBuilder historicalRoots(final SSZList<Bytes32> historicalRoots) {
    checkNotNull(historicalRoots);
    this.historicalRoots = historicalRoots;
    return this;
  }

  public BeaconStateBuilder eth1Data(final Eth1Data eth1Data) {
    checkNotNull(eth1Data);
    this.eth1Data = eth1Data;
    return this;
  }

  public BeaconStateBuilder eth1DataVotes(final SszList<Eth1Data> eth1DataVotes) {
    checkNotNull(eth1DataVotes);
    this.eth1DataVotes = eth1DataVotes;
    return this;
  }

  public BeaconStateBuilder eth1DepositIndex(final UInt64 eth1DepositIndex) {
    checkNotNull(eth1DepositIndex);
    this.eth1DepositIndex = eth1DepositIndex;
    return this;
  }

  public BeaconStateBuilder validators(final SszList<Validator> validators) {
    checkNotNull(validators);
    this.validators = validators;
    return this;
  }

  public BeaconStateBuilder balances(final SszUInt64List balances) {
    checkNotNull(balances);
    this.balances = balances;
    return this;
  }

  public BeaconStateBuilder randaoMixes(final SszBytes32Vector randaoMixes) {
    checkNotNull(randaoMixes);
    this.randaoMixes = randaoMixes;
    return this;
  }

  public BeaconStateBuilder slashings(final SszPrimitiveVector<UInt64, SszUInt64> slashings) {
    checkNotNull(slashings);
    this.slashings = slashings;
    return this;
  }

  public BeaconStateBuilder previousEpochAttestations(
      final SszList<PendingAttestation> previousEpochAttestations) {
    checkNotNull(previousEpochAttestations);
    this.previousEpochAttestations = previousEpochAttestations;
    return this;
  }

  public BeaconStateBuilder currentEpochAttestations(
      final SszList<PendingAttestation> currentEpochAttestations) {
    checkNotNull(currentEpochAttestations);
    this.currentEpochAttestations = currentEpochAttestations;
    return this;
  }

  public BeaconStateBuilder justificationBits(final SszBitvector justificationBits) {
    checkNotNull(justificationBits);
    this.justificationBits = justificationBits;
    return this;
  }

  public BeaconStateBuilder previousJustifiedCheckpoint(
      final Checkpoint previousJustifiedCheckpoint) {
    checkNotNull(previousJustifiedCheckpoint);
    this.previousJustifiedCheckpoint = previousJustifiedCheckpoint;
    return this;
  }

  public BeaconStateBuilder currentJustifiedCheckpoint(
      final Checkpoint currentJustifiedCheckpoint) {
    checkNotNull(currentJustifiedCheckpoint);
    this.currentJustifiedCheckpoint = currentJustifiedCheckpoint;
    return this;
  }

  public BeaconStateBuilder finalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
    checkNotNull(finalizedCheckpoint);
    this.finalizedCheckpoint = finalizedCheckpoint;
    return this;
  }

  public BeaconStateBuilder setJustifiedCheckpointsToEpoch(final UInt64 epoch) {
    final Checkpoint checkpoint = new Checkpoint(epoch, dataStructureUtil.randomBytes32());
    previousJustifiedCheckpoint(checkpoint);
    currentJustifiedCheckpoint(checkpoint);
    return this;
  }

  public BeaconStateBuilder setFinalizedCheckpointToEpoch(final UInt64 epoch) {
    return finalizedCheckpoint(new Checkpoint(epoch, dataStructureUtil.randomBytes32()));
  }
}
