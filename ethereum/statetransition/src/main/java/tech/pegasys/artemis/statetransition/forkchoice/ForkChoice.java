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

package tech.pegasys.teku.statetransition.forkchoice;

import static tech.pegasys.teku.core.ForkChoiceUtil.on_attestation;
import static tech.pegasys.teku.core.ForkChoiceUtil.on_block;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.results.AttestationProcessingResult;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.protoarray.ProtoArrayForkChoiceStrategy;
import tech.pegasys.teku.storage.Store;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoice implements FinalizedCheckpointChannel {

  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;

  private ProtoArrayForkChoiceStrategy protoArrayForkChoiceStrategy;

  public ForkChoice(final RecentChainData recentChainData, final StateTransition stateTransition) {
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
  }

  private void initializeProtoArrayForkChoice() {
    protoArrayForkChoiceStrategy = ProtoArrayForkChoiceStrategy.create(recentChainData.getStore());
  }

  public Bytes32 processHead() {
    Store.Transaction transaction = recentChainData.startStoreTransaction();
    Bytes32 headBlockRoot = protoArrayForkChoiceStrategy.findHead(transaction);
    transaction.commit(() -> {}, "Failed to persist validator vote changes.");
    BeaconBlock headBlock = recentChainData.getStore().getBlock(headBlockRoot);
    recentChainData.updateBestBlock(headBlockRoot, headBlock.getSlot());
    return headBlockRoot;
  }

  public BlockImportResult onBlock(final MutableStore store, final SignedBeaconBlock block) {
    return on_block(store, block, stateTransition, protoArrayForkChoiceStrategy);
  }

  public AttestationProcessingResult onAttestation(
      final MutableStore store, final Attestation attestation) {
    return on_attestation(store, attestation, stateTransition, protoArrayForkChoiceStrategy);
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    protoArrayForkChoiceStrategy.maybePrune(checkpoint.getRoot());
  }
}
