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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

class ExecutionPayloadEnvelopesRequestLimiterTest {

  private final NodeId peerA = new MockNodeId(1);
  private final NodeId peerB = new MockNodeId(2);
  private final NodeId peerC = new MockNodeId(3);

  @Test
  void shouldAdmitRequestsUpToTheLimit() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(2, 2);

    assertThat(limiter.tryAcquire(peerA)).isTrue();
    assertThat(limiter.tryAcquire(peerB)).isTrue();

    assertThat(limiter.tryAcquire(peerC)).isFalse();
  }

  @Test
  void shouldAdmitAnotherRequestOnceOneIsReleased() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(2, 2);
    assertThat(limiter.tryAcquire(peerA)).isTrue();
    assertThat(limiter.tryAcquire(peerB)).isTrue();

    limiter.release(peerA);

    assertThat(limiter.tryAcquire(peerC)).isTrue();
    assertThat(limiter.tryAcquire(peerA)).isFalse();
  }

  @Test
  void shouldNotAdmitMoreThanTheLimitAfterSpuriousRelease() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(2, 2);
    // A release without a matching acquisition must not raise the limit
    limiter.release(peerA);

    assertThat(limiter.tryAcquire(peerA)).isTrue();
    assertThat(limiter.tryAcquire(peerB)).isTrue();
    assertThat(limiter.tryAcquire(peerC)).isFalse();
  }

  @Test
  void shouldNotAdmitMoreThanThePeerShareForOnePeer() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(4, 2);
    assertThat(limiter.tryAcquire(peerA)).isTrue();
    assertThat(limiter.tryAcquire(peerA)).isTrue();

    // The limit still has room, but this peer has used its share
    assertThat(limiter.tryAcquire(peerA)).isFalse();
    assertThat(limiter.tryAcquire(peerB)).isTrue();
  }

  @Test
  void shouldAdmitPeerAgainOnceOneOfItsRequestsIsReleased() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(4, 2);
    assertThat(limiter.tryAcquire(peerA)).isTrue();
    assertThat(limiter.tryAcquire(peerA)).isTrue();

    limiter.release(peerA);

    assertThat(limiter.tryAcquire(peerA)).isTrue();
    assertThat(limiter.tryAcquire(peerA)).isFalse();
  }

  @Test
  void shouldNotLetAnotherPeersReleaseRaiseAPeersShare() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(4, 2);
    assertThat(limiter.tryAcquire(peerA)).isTrue();
    assertThat(limiter.tryAcquire(peerA)).isTrue();

    // A release for a peer with no request in flight must not free anything
    limiter.release(peerB);

    assertThat(limiter.tryAcquire(peerA)).isFalse();
  }
}
