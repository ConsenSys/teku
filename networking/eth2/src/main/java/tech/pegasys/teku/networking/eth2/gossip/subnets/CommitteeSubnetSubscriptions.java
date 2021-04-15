package tech.pegasys.teku.networking.eth2.gossip.subnets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;

abstract class CommitteeSubnetSubscriptions implements AutoCloseable {

  protected final GossipNetwork gossipNetwork;
  protected final GossipEncoding gossipEncoding;

  private final Map<Integer, TopicChannel> subnetIdToTopicChannel = new HashMap<>();

  protected CommitteeSubnetSubscriptions(
      final GossipNetwork gossipNetwork, final GossipEncoding gossipEncoding) {
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
  }

  protected synchronized Optional<TopicChannel> getChannelForSubnet(final int subnetId) {
    return Optional.ofNullable(subnetIdToTopicChannel.get(subnetId));
  }

  public synchronized void subscribeToSubnetId(final int subnetId) {
    subnetIdToTopicChannel.computeIfAbsent(subnetId, this::createChannelForSubnetId);
  }

  public synchronized void unsubscribeFromSubnetId(final int subnetId) {
    final TopicChannel topicChannel = subnetIdToTopicChannel.remove(subnetId);
    if (topicChannel != null) {
      topicChannel.close();
    }
  }

  private TopicChannel createChannelForSubnetId(final int subnetId) {
    final Eth2TopicHandler<?> topicHandler = createTopicHandler(subnetId);
    return gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
  }

  protected abstract Eth2TopicHandler<?> createTopicHandler(final int subnetId);

  @Override
  public synchronized void close() {
    // Close gossip channels
    subnetIdToTopicChannel.values().forEach(TopicChannel::close);
    subnetIdToTopicChannel.clear();
  }
}