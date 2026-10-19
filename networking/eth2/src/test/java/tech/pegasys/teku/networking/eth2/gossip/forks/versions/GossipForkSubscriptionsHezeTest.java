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

package tech.pegasys.teku.networking.eth2.gossip.forks.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadBidGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.InclusionListGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.PayloadAttestationMessageGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ProposerPreferencesGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class GossipForkSubscriptionsHezeTest {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final Fork fork = spec.getForkSchedule().getFork(UInt64.ZERO);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  @SuppressWarnings("rawtypes")
  private final DiscoveryNetwork discoveryNetwork = mock(DiscoveryNetwork.class);

  private final TopicChannel otherTopicChannel = mock(TopicChannel.class);
  private final TopicChannel inclusionListTopicChannel = mock(TopicChannel.class);

  private String inclusionListTopic;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    inclusionListTopic =
        GossipTopics.getTopic(
            recentChainData.getForkDigest(UInt64.ZERO),
            GossipTopicName.INCLUSION_LIST,
            gossipEncoding);

    when(otherTopicChannel.gossip(any())).thenReturn(SafeFuture.COMPLETE);
    when(inclusionListTopicChannel.gossip(any())).thenReturn(SafeFuture.COMPLETE);
    when(discoveryNetwork.subscribe(any(), any())).thenReturn(otherTopicChannel);
    when(discoveryNetwork.subscribe(eq(inclusionListTopic), any()))
        .thenReturn(inclusionListTopicChannel);
  }

  @Test
  public void shouldBeHezeFork() {
    assertThat(spec.atEpoch(fork.getEpoch()).getMilestone()).isEqualTo(SpecMilestone.HEZE);
  }

  @Test
  public void shouldAddRequiredGossipManagers() {
    final GossipForkSubscriptionsHeze gossipForkSubscriptions =
        spy(createGossipForkSubscriptionHeze());

    gossipForkSubscriptions.addGossipManagers(forkInfo(), forkDigest());

    // all the Gloas gossip managers plus the inclusion list gossip manager
    verify(gossipForkSubscriptions, times(15)).addGossipManager(any());
    verify(gossipForkSubscriptions).addGossipManager(any(ExecutionPayloadGossipManager.class));
    verify(gossipForkSubscriptions)
        .addGossipManager(any(PayloadAttestationMessageGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ExecutionPayloadBidGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ProposerPreferencesGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(InclusionListGossipManager.class));
  }

  @Test
  public void shouldSubscribeToInclusionListTopicWhenGossipStarts() {
    final GossipForkSubscriptionsHeze gossipForkSubscriptions = createGossipForkSubscriptionHeze();

    gossipForkSubscriptions.startGossip(genesisValidatorsRoot(), false);

    verify(discoveryNetwork).subscribe(eq(inclusionListTopic), any());
  }

  @Test
  public void shouldNotSubscribeToInclusionListTopicWhenHeadIsOptimistic() {
    final GossipForkSubscriptionsHeze gossipForkSubscriptions = createGossipForkSubscriptionHeze();

    gossipForkSubscriptions.startGossip(genesisValidatorsRoot(), true);

    verify(discoveryNetwork, never()).subscribe(eq(inclusionListTopic), any());
  }

  @Test
  public void shouldUnsubscribeFromInclusionListTopicWhenGossipStops() {
    final GossipForkSubscriptionsHeze gossipForkSubscriptions = createGossipForkSubscriptionHeze();
    gossipForkSubscriptions.startGossip(genesisValidatorsRoot(), false);

    gossipForkSubscriptions.stopGossip();

    verify(inclusionListTopicChannel).close();
  }

  @Test
  public void shouldPublishInclusionListOnInclusionListTopic() {
    final GossipForkSubscriptionsHeze gossipForkSubscriptions = createGossipForkSubscriptionHeze();
    gossipForkSubscriptions.startGossip(genesisValidatorsRoot(), false);
    final SignedInclusionList inclusionList = dataStructureUtil.randomSignedInclusionList();

    gossipForkSubscriptions.publishSignedInclusionList(inclusionList);

    verify(inclusionListTopicChannel).gossip(gossipEncoding.encode(inclusionList));
    verify(otherTopicChannel, never()).gossip(any());
  }

  @Test
  public void shouldNotPublishInclusionListAfterGossipStops() {
    final GossipForkSubscriptionsHeze gossipForkSubscriptions = createGossipForkSubscriptionHeze();
    gossipForkSubscriptions.startGossip(genesisValidatorsRoot(), false);
    gossipForkSubscriptions.stopGossip();

    gossipForkSubscriptions.publishSignedInclusionList(
        dataStructureUtil.randomSignedInclusionList());

    verify(inclusionListTopicChannel, never()).gossip(any());
  }

  private Bytes32 genesisValidatorsRoot() {
    return recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot();
  }

  private ForkInfo forkInfo() {
    return new ForkInfo(fork, dataStructureUtil.randomBytes32());
  }

  private Bytes4 forkDigest() {
    return dataStructureUtil.randomBytes4();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private GossipForkSubscriptionsHeze createGossipForkSubscriptionHeze() {
    final OperationProcessor noopOperationProcessor = OperationProcessor.NOOP;

    return new GossipForkSubscriptionsHeze(
        fork,
        spec,
        new StubAsyncRunner(),
        new StubMetricsSystem(),
        discoveryNetwork,
        recentChainData,
        gossipEncoding,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        Optional.empty(),
        Optional.empty(),
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        DebugDataDumper.NOOP,
        DasGossipLogger.NOOP,
        noopOperationProcessor,
        false,
        () -> false);
  }
}
