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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import tech.pegasys.teku.networking.eth2.SubnetSubscriptionService;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.ssz.collections.SszBitvector;
import tech.pegasys.teku.ssz.schema.collections.SszBitvectorSchema;

public class PeerSubnetSubscriptions {

  private final SubnetSubscriptions attestationSubnetSubscriptions;
  private final SubnetSubscriptions syncCommitteeSubnetSubscriptions;
  private final int targetSubnetSubscriberCount;

  private PeerSubnetSubscriptions(
      final SubnetSubscriptions attestationSubnetSubscriptions,
      final SubnetSubscriptions syncCommitteeSubnetSubscriptions,
      final int targetSubnetSubscriberCount) {
    this.attestationSubnetSubscriptions = attestationSubnetSubscriptions;
    this.syncCommitteeSubnetSubscriptions = syncCommitteeSubnetSubscriptions;
    this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
  }

  public static PeerSubnetSubscriptions create(
      final SchemaDefinitionsSupplier currentSchemaDefinitions,
      final GossipNetwork network,
      final AttestationSubnetTopicProvider attestationTopicProvider,
      final SyncCommitteeSubnetTopicProvider syncCommitteeSubnetTopicProvider,
      final SubnetSubscriptionService syncCommitteeSubnetService,
      final int targetSubnetSubscriberCount) {
    final Map<String, Collection<NodeId>> subscribersByTopic = network.getSubscribersByTopic();

    return builder(currentSchemaDefinitions)
        .targetSubnetSubscriberCount(targetSubnetSubscriberCount)
        .attestationSubnetSubscriptions(
            b ->
                streamAllAttestationSubnetIds(currentSchemaDefinitions)
                    .forEach(
                        attestationSubnet -> {
                          b.addRelevantSubnet(attestationSubnet);
                          subscribersByTopic
                              .getOrDefault(
                                  attestationTopicProvider.getTopicForSubnet(attestationSubnet),
                                  Collections.emptySet())
                              .forEach(
                                  subscriber -> b.addSubscriber(attestationSubnet, subscriber));
                        }))
        .syncCommitteeSubnetSubscriptions(
            b ->
                syncCommitteeSubnetService
                    .getSubnets()
                    .forEach(
                        syncCommitteeSubnet -> {
                          b.addRelevantSubnet(syncCommitteeSubnet);
                          subscribersByTopic
                              .getOrDefault(
                                  syncCommitteeSubnetTopicProvider.getTopicForSubnet(
                                      syncCommitteeSubnet),
                                  Collections.emptySet())
                              .forEach(
                                  subscriber -> b.addSubscriber(syncCommitteeSubnet, subscriber));
                        }))
        .build();
  }

  private static IntStream streamAllAttestationSubnetIds(
      final SchemaDefinitionsSupplier currentSchemaDefinitions) {
    return IntStream.range(0, currentSchemaDefinitions.getAttnetsSchema().getLength());
  }

  public static Builder builder(final SchemaDefinitionsSupplier currentSchemaDefinitions) {
    return new Builder(currentSchemaDefinitions);
  }

  @VisibleForTesting
  public static PeerSubnetSubscriptions createEmpty(
      final SchemaDefinitionsSupplier currentSchemaDefinitions) {
    return builder(currentSchemaDefinitions).build();
  }

  public int getSubscriberCountForAttestationSubnet(final int subnetId) {
    return attestationSubnetSubscriptions.getSubscriberCountForSubnet(subnetId);
  }

  public int getSubscriberCountForSyncCommitteeSubnet(final int subnetId) {
    return syncCommitteeSubnetSubscriptions.getSubscriberCountForSubnet(subnetId);
  }

  public SszBitvector getAttestationSubnetSubscriptions(final NodeId peerId) {
    return attestationSubnetSubscriptions.getSubnetSubscriptions(peerId);
  }

  public SszBitvector getSyncCommitteeSubscriptions(final NodeId peerId) {
    return syncCommitteeSubnetSubscriptions.getSubnetSubscriptions(peerId);
  }

  private Optional<Integer> getMinSubscriberCount() {
    final Optional<Integer> minAttestationSubscribers = getMinAttestationSubscriberCount();
    final Optional<Integer> minSyncnetSubscribers = getMinSyncCommitteeSubscriberCount();
    if (minAttestationSubscribers.isPresent() && minSyncnetSubscribers.isPresent()) {
      return Optional.of(Math.min(minAttestationSubscribers.get(), minSyncnetSubscribers.get()));
    } else {
      return minAttestationSubscribers.or(() -> minSyncnetSubscribers);
    }
  }

  private Optional<Integer> getMinAttestationSubscriberCount() {
    return attestationSubnetSubscriptions
        .streamRelevantSubnets()
        .map(attestationSubnetSubscriptions::getSubscriberCountForSubnet)
        .min(Integer::compare);
  }

  private Optional<Integer> getMinSyncCommitteeSubscriberCount() {
    return syncCommitteeSubnetSubscriptions
        .streamRelevantSubnets()
        .map(syncCommitteeSubnetSubscriptions::getSubscriberCountForSubnet)
        .min(Integer::compare);
  }

  public PeerScorer createScorer() {
    return SubnetScorer.create(this);
  }

  public int getSubscribersRequired() {
    return getMinSubscriberCount().map(c -> Math.max(targetSubnetSubscriberCount - c, 0)).orElse(0);
  }

  public interface Factory {

    /**
     * Creates a new PeerSubnetSubscriptions which reports the subscriptions from the supplied
     * network at time of creation.
     *
     * @param gossipNetwork the network to load subscriptions from
     * @return the new PeerSubnetSubscriptions
     */
    PeerSubnetSubscriptions create(GossipNetwork gossipNetwork);
  }

  public static class SubnetSubscriptions {
    private final SszBitvectorSchema<?> subscriptionSchema;
    private final List<Integer> relevantSubnets;
    private final Map<Integer, Integer> subscriberCountBySubnetId;
    private final Map<NodeId, SszBitvector> subscriptionsByPeer;

    private SubnetSubscriptions(
        final SszBitvectorSchema<?> subscriptionSchema,
        final List<Integer> relevantSubnets,
        final Map<Integer, Integer> subscriberCountBySubnetId,
        final Map<NodeId, SszBitvector> subscriptionsByPeer) {
      this.subscriptionSchema = subscriptionSchema;
      this.relevantSubnets = relevantSubnets;
      this.subscriberCountBySubnetId = subscriberCountBySubnetId;
      this.subscriptionsByPeer = subscriptionsByPeer;
    }

    public static Builder builder(SszBitvectorSchema<?> subscriptionSchema) {
      return new Builder(subscriptionSchema);
    }

    public List<Integer> getRelevantSubnets() {
      return relevantSubnets;
    }

    public Stream<Integer> streamRelevantSubnets() {
      return getRelevantSubnets().stream();
    }

    public int getSubscriberCountForSubnet(final int subnetId) {
      return subscriberCountBySubnetId.getOrDefault(subnetId, 0);
    }

    public SszBitvector getSubnetSubscriptions(final NodeId peerId) {
      return subscriptionsByPeer.getOrDefault(peerId, subscriptionSchema.getDefault());
    }

    public static class Builder {
      private final SszBitvectorSchema<?> subscriptionSchema;

      private final List<Integer> relevantSubnets = new ArrayList<>();
      private final Map<Integer, Integer> subscriberCountBySubnetId = new HashMap<>();
      private final Map<NodeId, SszBitvector> subscriptionsByPeer = new HashMap<>();

      private Builder(final SszBitvectorSchema<?> subscriptionSchema) {
        this.subscriptionSchema = subscriptionSchema;
      }

      public Builder addRelevantSubnet(final int subnetId) {
        relevantSubnets.add(subnetId);
        return this;
      }

      public Builder addSubscriber(final int subnetId, final NodeId peer) {
        subscriberCountBySubnetId.put(
            subnetId, subscriberCountBySubnetId.getOrDefault(subnetId, 0) + 1);
        subscriptionsByPeer.compute(
            peer,
            (__, existingVector) ->
                existingVector == null
                    ? subscriptionSchema.ofBits(subnetId)
                    : existingVector.withBit(subnetId));
        return this;
      }

      public SubnetSubscriptions build() {
        return new SubnetSubscriptions(
            subscriptionSchema, relevantSubnets, subscriberCountBySubnetId, subscriptionsByPeer);
      }
    }
  }

  public static class Builder {
    private final SubnetSubscriptions.Builder attestationSubnetSubscriptions;
    private final SubnetSubscriptions.Builder syncCommitteeSubnetSubscriptions;
    private int targetSubnetSubscriberCount = 2;

    private Builder(final SchemaDefinitionsSupplier currentSchemaDefinitions) {
      attestationSubnetSubscriptions =
          SubnetSubscriptions.builder(currentSchemaDefinitions.getAttnetsSchema());
      syncCommitteeSubnetSubscriptions =
          SubnetSubscriptions.builder(currentSchemaDefinitions.getSyncnetsSchema());
    }

    public PeerSubnetSubscriptions build() {
      return new PeerSubnetSubscriptions(
          attestationSubnetSubscriptions.build(),
          syncCommitteeSubnetSubscriptions.build(),
          targetSubnetSubscriberCount);
    }

    public Builder targetSubnetSubscriberCount(final Integer targetSubnetSubscriberCount) {
      checkNotNull(targetSubnetSubscriberCount);
      this.targetSubnetSubscriberCount = targetSubnetSubscriberCount;
      return this;
    }

    public Builder attestationSubnetSubscriptions(
        final Consumer<SubnetSubscriptions.Builder> consumer) {
      consumer.accept(attestationSubnetSubscriptions);
      return this;
    }

    public Builder syncCommitteeSubnetSubscriptions(
        final Consumer<SubnetSubscriptions.Builder> consumer) {
      consumer.accept(syncCommitteeSubnetSubscriptions);
      return this;
    }
  }
}
