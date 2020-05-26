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

package tech.pegasys.teku.networking.eth2.gossip;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.AggregateAttestationTopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.UpstreamAttestationPipe;
import tech.pegasys.teku.networking.eth2.gossip.topics.validation.SignedAggregateAndProofValidator;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;

public class AggregateGossipManager {
  private final GossipEncoding gossipEncoding;
  private final TopicChannel channel;
  private final EventBus eventBus;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  public AggregateGossipManager(
          final GossipNetwork gossipNetwork,
          final GossipEncoding gossipEncoding,
          final ForkInfo forkInfo,
          final SignedAggregateAndProofValidator validator,
          final UpstreamAttestationPipe upstreamAttestationPipe,
          final EventBus eventBus) {
    this.gossipEncoding = gossipEncoding;
    final AggregateAttestationTopicHandler aggregateAttestationTopicHandler =
        new AggregateAttestationTopicHandler(gossipEncoding, forkInfo, validator, upstreamAttestationPipe);
    this.channel = gossipNetwork.subscribe(aggregateAttestationTopicHandler.getTopic(), aggregateAttestationTopicHandler);

    this.eventBus = eventBus;
    eventBus.register(this);
  }

  @Subscribe
  public void onNewAggregate(final SignedAggregateAndProof aggregateAndProof) {
    final Bytes data = gossipEncoding.encode(aggregateAndProof);
    channel.gossip(data);
  }

  public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
      eventBus.unregister(this);
      // Close gossip channels
      channel.close();
    }
  }
}
