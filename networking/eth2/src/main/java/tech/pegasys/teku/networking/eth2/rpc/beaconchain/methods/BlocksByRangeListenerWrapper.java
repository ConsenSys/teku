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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseStreamListener;
import tech.pegasys.teku.networking.p2p.peer.Peer;

import java.util.Optional;

public class BlocksByRangeListenerWrapper implements ResponseStreamListener<SignedBeaconBlock> {

  private final Peer peer;
  private final ResponseStreamListener<SignedBeaconBlock> blockResponseListener;
  private final UInt64 startSlot;
  private final UInt64 endSlot;
  private final UInt64 step;

  private Optional<Bytes32> maybeRootOfLastBlock = Optional.empty();
  private Optional<UInt64> maybeSlotOfLastBlock = Optional.empty();

  public BlocksByRangeListenerWrapper(
      Peer peer,
      ResponseStreamListener<SignedBeaconBlock> blockResponseListener,
      UInt64 startSlot,
      UInt64 count,
      UInt64 step) {
    this.peer = peer;
    this.blockResponseListener = blockResponseListener;
    this.startSlot = startSlot;
    this.step = step;
    this.endSlot = startSlot.plus(step.times(count));
  }

  @Override
  public SafeFuture<?> onResponse(SignedBeaconBlock response) {
    return SafeFuture.of(() -> {
      UInt64 blockSlot = response.getSlot();
      if (!blockSlotIsInRange(blockSlot)
              || !blockSlotMatchesTheStep(blockSlot)
              || !blockSlotGreaterThanPreviousBlockSlot(blockSlot)
              || !blockParentRootMatches(response.getParent_root())) {
        throw new BlocksByRangeResponseOutOfOrderException(peer, startSlot, endSlot);
      }
      maybeSlotOfLastBlock = Optional.of(blockSlot);
      maybeRootOfLastBlock = Optional.of(response.getRoot());
      return blockResponseListener.onResponse(response);
    });
  }

  private boolean blockSlotIsInRange(UInt64 blockSlot) {
    return blockSlot.compareTo(startSlot) >= 0 && blockSlot.compareTo(endSlot) <= 0;
  }

  private boolean blockSlotMatchesTheStep(UInt64 blockSlot) {
    return blockSlot.minus(startSlot).mod(step).equals(UInt64.ZERO);
  }

  private boolean blockSlotGreaterThanPreviousBlockSlot(UInt64 blockSlot) {
    if (maybeSlotOfLastBlock.isEmpty()) {
      return true;
    }

    UInt64 lastBlockSlot = maybeSlotOfLastBlock.get();
    return blockSlot.compareTo(lastBlockSlot) > 0;
  }

  private boolean blockParentRootMatches(Bytes32 blockParentRoot) {
    if (maybeRootOfLastBlock.isEmpty()) {
      return true;
    }

    if (!step.equals(UInt64.ONE)) {
      return true;
    }

    return maybeRootOfLastBlock.get().equals(blockParentRoot);
  }
}
