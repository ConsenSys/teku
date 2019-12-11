/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.eth2.peers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer.StatusData;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.config.Constants;

public class PeerChainValidatorTest {

  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final Store store = mock(Store.class);
  private final ChainStorageClient storageClient = mock(ChainStorageClient.class);

  private final Bytes32 randomBlockRoot = DataStructureUtil.randomBytes32(1);
  private final Bytes4 remoteFork = new Bytes4(Bytes.fromHexString("0x1234", 4));
  private final Bytes4 otherFork = new Bytes4(Bytes.fromHexString("0x3333", 4));
  private final Checkpoint remoteFinalizedCheckpoint =
      new Checkpoint(UnsignedLong.valueOf(10L), Bytes32.fromHexString("0x5555"));
  private final Checkpoint earlierCheckpoint =
      new Checkpoint(UnsignedLong.valueOf(8L), Bytes32.fromHexString("0x4444"));
  private final Checkpoint laterCheckpoint =
      new Checkpoint(UnsignedLong.valueOf(12L), Bytes32.fromHexString("0x6666"));
  private final StatusData remoteStatus = createStatusData();

  private final PeerChainValidator peerChainValidator =
      PeerChainValidator.create(storageClient, peer);

  @BeforeEach
  public void setup() {
    when(storageClient.getStore()).thenReturn(store);
    when(peer.hasStatus()).thenReturn(true);
    when(peer.getStatus()).thenReturn(remoteStatus);
  }

  @Test
  public void chainsAreCompatible_finalizedCheckpointsMatch() {
    // Setup mocks
    forksMatch();
    finalizedCheckpointsMatch();

    peerChainValidator.run();
    assertPeerChainVerified();
  }

  @Test
  public void chainsAreCompatible_remoteChainIsAhead() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadOnSameChain();

    peerChainValidator.run();
    assertPeerChainVerified();
  }

  @Test
  public void chainsAreCompatible_remoteChainIsBehind() {
    // Setup mocks
    forksMatch();
    remoteChainIsBehindOnSameChain();

    peerChainValidator.run();
    assertPeerChainVerified();
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsBehindOnDifferentChain() {
    // Setup mocks
    forksMatch();
    remoteChainIsBehindOnDifferentChain();

    peerChainValidator.run();
    assertPeerChainRejected(GoodbyeMessage.REASON_IRRELEVANT_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsAheadOnDifferentChain() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadOnDifferentChain();

    peerChainValidator.run();
    assertPeerChainRejected(GoodbyeMessage.REASON_IRRELEVANT_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_remoteChainIsAhead_peerUnresponsive() {
    // Setup mocks
    forksMatch();
    remoteChainIsAheadAndUnresponsive();

    peerChainValidator.run();
    assertPeerChainRejected(GoodbyeMessage.REASON_UNABLE_TO_VERIFY_NETWORK);
  }

  @Test
  public void chainsAreIncompatible_differentForks_finalizedCheckpointsMatch() {
    // Setup mocks
    forksDontMatch();

    peerChainValidator.run();
    assertPeerChainRejected(GoodbyeMessage.REASON_IRRELEVANT_NETWORK);
    // Verify other checks were skipped when fork mismatch was detected
    verify(peer, never()).requestBlockBySlot(any(), any());
    verify(storageClient, never()).getBlockAtOrPriorToSlot(any());
    verify(store, never()).getFinalizedCheckpoint();
  }

  private void assertPeerChainRejected(UnsignedLong goodbyeReason) {
    verify(peer, never()).markChainValidated();
    verify(peer).sendGoodbye(goodbyeReason);
  }

  private void assertPeerChainVerified() {
    verify(peer).markChainValidated();
    verify(peer, never()).sendGoodbye(any());
  }

  private void forksMatch() {
    when(storageClient.getForkAtSlot(remoteStatus.getHeadSlot())).thenReturn(remoteFork);
  }

  private void forksDontMatch() {
    when(storageClient.getForkAtSlot(remoteStatus.getHeadSlot())).thenReturn(otherFork);
  }

  private void finalizedCheckpointsMatch() {
    final Checkpoint remoteFinalizedCheckpoint = getFinalizedCheckpoint(remoteStatus);
    when(store.getFinalizedCheckpoint()).thenReturn(remoteFinalizedCheckpoint);
  }

  private void remoteChainIsAheadOnSameChain() {
    when(store.getFinalizedCheckpoint()).thenReturn(earlierCheckpoint);
    final UnsignedLong localFinalizedSlot =
        compute_start_slot_at_epoch(earlierCheckpoint.getEpoch());
    final CompletableFuture<BeaconBlock> blockFuture =
        CompletableFuture.completedFuture(blockWithRoot(earlierCheckpoint.getRoot()));
    when(peer.requestBlockBySlot(remoteStatus.getHeadRoot(), localFinalizedSlot))
        .thenReturn(blockFuture);
  }

  private void remoteChainIsAheadOnDifferentChain() {
    when(store.getFinalizedCheckpoint()).thenReturn(earlierCheckpoint);
    final UnsignedLong localFinalizedSlot =
        compute_start_slot_at_epoch(earlierCheckpoint.getEpoch());
    final CompletableFuture<BeaconBlock> blockFuture =
        CompletableFuture.completedFuture(blockWithRoot(randomBlockRoot));
    when(peer.requestBlockBySlot(remoteStatus.getHeadRoot(), localFinalizedSlot))
        .thenReturn(blockFuture);
  }

  private void remoteChainIsAheadAndUnresponsive() {
    when(store.getFinalizedCheckpoint()).thenReturn(earlierCheckpoint);
    final UnsignedLong localFinalizedSlot =
        compute_start_slot_at_epoch(earlierCheckpoint.getEpoch());
    final CompletableFuture<BeaconBlock> blockFuture =
        CompletableFuture.failedFuture(new NullPointerException());
    when(peer.requestBlockBySlot(remoteStatus.getHeadRoot(), localFinalizedSlot))
        .thenReturn(blockFuture);
  }

  private void remoteChainIsBehindOnSameChain() {
    final UnsignedLong remoteFinalizedSlot =
        compute_start_slot_at_epoch(remoteStatus.getFinalizedEpoch());
    when(store.getFinalizedCheckpoint()).thenReturn(laterCheckpoint);
    Optional<BeaconBlock> blockResult = Optional.of(blockWithRoot(remoteStatus.getFinalizedRoot()));
    when(storageClient.getBlockAtOrPriorToSlot(remoteFinalizedSlot)).thenReturn(blockResult);
  }

  private void remoteChainIsBehindOnDifferentChain() {
    final UnsignedLong remoteFinalizedSlot =
        compute_start_slot_at_epoch(remoteStatus.getFinalizedEpoch());
    when(store.getFinalizedCheckpoint()).thenReturn(laterCheckpoint);
    Optional<BeaconBlock> blockResult = Optional.of(blockWithRoot(randomBlockRoot));
    when(storageClient.getBlockAtOrPriorToSlot(remoteFinalizedSlot)).thenReturn(blockResult);
  }

  private BeaconBlock blockWithRoot(Bytes32 signingRoot) {
    final BeaconBlock block = mock(BeaconBlock.class);
    when(block.signing_root("signature")).thenReturn(signingRoot);
    return block;
  }

  private Checkpoint getFinalizedCheckpoint(final StatusData status) {
    return new Checkpoint(status.getFinalizedEpoch(), status.getFinalizedRoot());
  }

  private StatusData createStatusData() {

    final Bytes32 headRoot = Bytes32.fromHexString("0xeeee");
    // Set a head slot some distance beyond the finalized epoch
    final UnsignedLong headSlot =
        remoteFinalizedCheckpoint
            .getEpoch()
            .times(UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH))
            .plus(UnsignedLong.valueOf(10L));

    return new StatusData(
        remoteFork,
        remoteFinalizedCheckpoint.getRoot(),
        remoteFinalizedCheckpoint.getEpoch(),
        headRoot,
        headSlot);
  }
}
