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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
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
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class GossipForkSubscriptionsGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final Fork fork = spec.getForkSchedule().getFork(UInt64.ZERO);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void shouldAddRequiredGossipManagers() {
    final GossipForkSubscriptionsGloas gossipForkSubscriptions =
        spy(createGossipForkSubscriptionGloas());

    gossipForkSubscriptions.addGossipManagers(forkInfo(), forkDigest());

    verify(gossipForkSubscriptions, times(14)).addGossipManager(any());
    verify(gossipForkSubscriptions).addGossipManager(any(ExecutionPayloadGossipManager.class));
    verify(gossipForkSubscriptions)
        .addGossipManager(any(PayloadAttestationMessageGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ExecutionPayloadBidGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ProposerPreferencesGossipManager.class));
  }

  @Test
  public void shouldNotAddInclusionListGossipManager() {
    final GossipForkSubscriptionsGloas gossipForkSubscriptions =
        spy(createGossipForkSubscriptionGloas());

    gossipForkSubscriptions.addGossipManagers(forkInfo(), forkDigest());

    verify(gossipForkSubscriptions, never())
        .addGossipManager(any(InclusionListGossipManager.class));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void shouldNotSubscribeToOrPublishOnInclusionListTopic() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    final RecentChainData recentChainData = storageSystem.recentChainData();
    final DiscoveryNetwork discoveryNetwork = mock(DiscoveryNetwork.class);
    final TopicChannel topicChannel = mock(TopicChannel.class);
    when(discoveryNetwork.subscribe(any(), any())).thenReturn(topicChannel);
    final GossipForkSubscriptionsGloas gossipForkSubscriptions =
        createGossipForkSubscriptionGloas(
            discoveryNetwork, recentChainData, GossipEncoding.SSZ_SNAPPY);

    gossipForkSubscriptions.startGossip(
        recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot(), false);

    final SignedInclusionList inclusionList =
        new DataStructureUtil(TestSpecFactory.createMinimalHeze()).randomSignedInclusionList();
    gossipForkSubscriptions.publishSignedInclusionList(inclusionList);

    verify(discoveryNetwork, never())
        .subscribe(contains(GossipTopicName.INCLUSION_LIST.toString()), any());
    verify(topicChannel, never()).gossip(any());
  }

  private ForkInfo forkInfo() {
    return new ForkInfo(fork, dataStructureUtil.randomBytes32());
  }

  private Bytes4 forkDigest() {
    return dataStructureUtil.randomBytes4();
  }

  private GossipForkSubscriptionsGloas createGossipForkSubscriptionGloas() {
    return createGossipForkSubscriptionGloas(
        mock(DiscoveryNetwork.class),
        MemoryOnlyRecentChainData.create(spec),
        mock(GossipEncoding.class));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private GossipForkSubscriptionsGloas createGossipForkSubscriptionGloas(
      final DiscoveryNetwork discoveryNetwork,
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding) {
    final OperationProcessor noopOperationProcessor = OperationProcessor.NOOP;

    return new GossipForkSubscriptionsGloas(
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
        DebugDataDumper.NOOP,
        DasGossipLogger.NOOP,
        noopOperationProcessor,
        false,
        () -> false);
  }
}
