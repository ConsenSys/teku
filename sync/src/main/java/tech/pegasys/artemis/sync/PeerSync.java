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

package tech.pegasys.artemis.sync;

import static tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage.REASON_FAULT_ERROR;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.util.config.Constants.MAX_BLOCK_BY_RANGE_REQUEST_SIZE;

import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.artemis.statetransition.BlockImporter;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.storage.ChainStorageClient;

public class PeerSync {

  private static final UnsignedLong STEP = UnsignedLong.ONE;

  private Eth2Peer peer;
  private final UnsignedLong advertisedFinalizedEpoch;
  private final UnsignedLong advertisedHeadBlockSlot;
  private final Bytes32 advertisedHeadBlockRoot;
  private final ChainStorageClient storageClient;
  private final BlockImporter blockImporter;

  private UnsignedLong latestRequestedSlot;
  private final CompletableFuture<PeerSyncResult> finalResult = new CompletableFuture<>();

  public PeerSync(
      final Eth2Peer peer,
      final ChainStorageClient storageClient,
      final BlockImporter blockImporter) {
    this.peer = peer;
    this.storageClient = storageClient;
    this.blockImporter = blockImporter;

    this.advertisedFinalizedEpoch = peer.getStatus().getFinalizedEpoch();
    this.advertisedHeadBlockSlot = peer.getStatus().getHeadSlot();
    this.advertisedHeadBlockRoot = peer.getStatus().getHeadRoot();
    this.latestRequestedSlot = compute_start_slot_at_epoch(storageClient.getFinalizedEpoch());
  }

  public CompletableFuture<PeerSyncResult> sync() {
    executeSync();
    return finalResult;
  }

  private void executeSync() {
    requestSyncBlocks(peer)
        .whenComplete(
            (res, err) -> {
              if (err != null) {
                Throwable rootException = Throwables.getRootCause(err);
                if (rootException instanceof BadBlockException) {
                  disconnectFromPeer(peer);
                }
                finalResult.completeExceptionally(err);
              } else if (storageClient.getFinalizedEpoch().compareTo(advertisedFinalizedEpoch)
                  >= 0) {
                finalResult.complete(PeerSyncResult.SUCCESSFUL_SYNC);
              } else if (latestRequestedSlot.compareTo(advertisedHeadBlockSlot) < 0) {
                executeSync();
              } else {
                finalResult.completeExceptionally(new FaultyAdvertisementException());
                disconnectFromPeer(peer);
              }
            });
  }

  private CompletableFuture<Void> requestSyncBlocks(Eth2Peer peer) {
    UnsignedLong diff = advertisedHeadBlockSlot.minus(latestRequestedSlot);
    UnsignedLong count =
        diff.compareTo(MAX_BLOCK_BY_RANGE_REQUEST_SIZE) > 0
            ? MAX_BLOCK_BY_RANGE_REQUEST_SIZE
            : diff;
    latestRequestedSlot = latestRequestedSlot.plus(count);
    return peer.requestBlocksByRange(
        advertisedHeadBlockRoot, latestRequestedSlot, count, STEP, this::blockResponseListener);
  }

  private void blockResponseListener(BeaconBlock block) {
    try {
      blockImporter.importBlock(block);
    } catch (StateTransitionException e) {
      throw new BadBlockException("State transition error", e);
    }
  }

  private void disconnectFromPeer(Eth2Peer peer) {
    peer.sendGoodbye(REASON_FAULT_ERROR);
  }

  public static class BadBlockException extends InvalidResponseException {
    public BadBlockException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class FaultyAdvertisementException extends RuntimeException {}
}
