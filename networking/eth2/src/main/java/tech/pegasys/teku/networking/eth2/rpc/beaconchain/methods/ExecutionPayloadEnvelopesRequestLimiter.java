/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/**
 * Caps concurrent execution payload envelope requests, in total and per peer, since serving them
 * may require EL calls. Excess requests are rejected rather than queued.
 */
public class ExecutionPayloadEnvelopesRequestLimiter {

  private final int maxConcurrentRequests;
  private final int maxConcurrentRequestsPerPeer;
  private final Map<NodeId, Integer> activeRequestsByPeer = new HashMap<>();
  private int activeRequests = 0;

  public ExecutionPayloadEnvelopesRequestLimiter(
      final int maxConcurrentRequests, final int maxConcurrentRequestsPerPeer) {
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.maxConcurrentRequestsPerPeer = maxConcurrentRequestsPerPeer;
  }

  /** Each successful acquisition must be followed by exactly one {@link #release(NodeId)}. */
  public synchronized boolean tryAcquire(final NodeId peerId) {
    final int peerActiveRequests = activeRequestsByPeer.getOrDefault(peerId, 0);
    if (activeRequests >= maxConcurrentRequests
        || peerActiveRequests >= maxConcurrentRequestsPerPeer) {
      return false;
    }
    activeRequests++;
    activeRequestsByPeer.put(peerId, peerActiveRequests + 1);
    return true;
  }

  public synchronized void release(final NodeId peerId) {
    // Ignore unmatched releases
    final int peerActiveRequests = activeRequestsByPeer.getOrDefault(peerId, 0);
    if (peerActiveRequests == 0) {
      return;
    }
    activeRequests--;
    if (peerActiveRequests == 1) {
      activeRequestsByPeer.remove(peerId);
    } else {
      activeRequestsByPeer.put(peerId, peerActiveRequests - 1);
    }
  }
}
