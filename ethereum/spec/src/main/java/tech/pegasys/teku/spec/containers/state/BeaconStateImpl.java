/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.containers.state;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszContainerImpl;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;

class BeaconStateImpl extends SszContainerImpl implements BeaconState {
  public BeaconStateImpl(BeaconStateSchema schema) {
    super(schema);
  }

  BeaconStateImpl(SszCompositeSchema<?> type, TreeNode backingNode, IntCache<SszData> cache) {
    super(type, backingNode, cache);
  }

  BeaconStateImpl(SszContainerSchema<? extends SszContainer> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  // Example getters

  @Override
  public UInt64 getGenesis_time() {
    final int fieldIndex = schema.getFieldIndex(BeaconStateFields.GENESIS_TIME.name());
    return ((SszPrimitives.SszUInt64) get(fieldIndex)).get();
  }

  @Override
  public Optional<SSZList<PendingAttestation>> maybeGetPrevious_epoch_attestations() {
    return Optional.ofNullable(
            schema.getField(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name()))
        .map(
            field ->
                new SSZBackingList<>(
                    PendingAttestation.class,
                    getAny(field.getIndex()),
                    Function.identity(),
                    Function.identity()));
  }

  // TODO implement other methods below

  @Override
  public Bytes32 getGenesis_validators_root() {
    return null;
  }

  @Override
  public UInt64 getSlot() {
    return null;
  }

  @Override
  public Fork getFork() {
    return null;
  }

  @Override
  public ForkInfo getForkInfo() {
    return null;
  }

  @Override
  public BeaconBlockHeader getLatest_block_header() {
    return null;
  }

  @Override
  public SSZVector<Bytes32> getBlock_roots() {
    return null;
  }

  @Override
  public SSZVector<Bytes32> getState_roots() {
    return null;
  }

  @Override
  public SSZList<Bytes32> getHistorical_roots() {
    return null;
  }

  @Override
  public Eth1Data getEth1_data() {
    return null;
  }

  @Override
  public SSZList<Eth1Data> getEth1_data_votes() {
    return null;
  }

  @Override
  public UInt64 getEth1_deposit_index() {
    return null;
  }

  @Override
  public SSZList<Validator> getValidators() {
    return null;
  }

  @Override
  public SSZList<UInt64> getBalances() {
    return null;
  }

  @Override
  public SSZVector<Bytes32> getRandao_mixes() {
    return null;
  }

  @Override
  public SSZVector<UInt64> getSlashings() {
    return null;
  }

  @Override
  public Bitvector getJustification_bits() {
    return null;
  }

  @Override
  public Checkpoint getPrevious_justified_checkpoint() {
    return null;
  }

  @Override
  public Checkpoint getCurrent_justified_checkpoint() {
    return null;
  }

  @Override
  public Checkpoint getFinalized_checkpoint() {
    return null;
  }

  @Override
  public Optional<SSZList<PendingAttestation>> maybeGetCurrent_epoch_attestations() {
    return Optional.empty();
  }

  @Override
  public Optional<SSZList<SSZVector<SszPrimitives.SszBit>>> maybeGetPreviousEpochParticipation() {
    return Optional.empty();
  }

  @Override
  public Optional<SSZList<SSZVector<SszPrimitives.SszBit>>> maybeGetCurrentEpochParticipation() {
    return Optional.empty();
  }

  @Override
  public BeaconState update(final Consumer<MutableBeaconState> updater) {
    return null;
  }
}
