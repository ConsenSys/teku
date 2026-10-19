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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.InclusionListGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class GossipForkSubscriptionsHezeBpoTest {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final Fork fork = spec.getForkSchedule().getFork(UInt64.ZERO);
  private final BlobParameters bpo = new BlobParameters(UInt64.valueOf(3), 64);
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
            recentChainData.getForkDigest(bpo.epoch()),
            GossipTopicName.INCLUSION_LIST,
            gossipEncoding);

    when(otherTopicChannel.gossip(any())).thenReturn(SafeFuture.COMPLETE);
    when(inclusionListTopicChannel.gossip(any())).thenReturn(SafeFuture.COMPLETE);
    when(discoveryNetwork.subscribe(any(), any())).thenReturn(otherTopicChannel);
    when(discoveryNetwork.subscribe(eq(inclusionListTopic), any()))
        .thenReturn(inclusionListTopicChannel);
  }

  @Test
  public void shouldActivateAtBpoEpoch() {
    assertThat(createGossipForkSubscriptionHezeBpo().getActivationEpoch()).isEqualTo(bpo.epoch());
  }

  @Test
  public void shouldAddInclusionListGossipManager() {
    final GossipForkSubscriptionsHezeBpo gossipForkSubscriptions =
        spy(createGossipForkSubscriptionHezeBpo());

    gossipForkSubscriptions.addGossipManagers(
        new ForkInfo(fork, dataStructureUtil.randomBytes32()), dataStructureUtil.randomBytes4());

    verify(gossipForkSubscriptions).addGossipManager(any(InclusionListGossipManager.class));
  }

  @Test
  public void shouldPublishInclusionListOnBpoInclusionListTopic() {
    final GossipForkSubscriptionsHezeBpo gossipForkSubscriptions =
        createGossipForkSubscriptionHezeBpo();
    gossipForkSubscriptions.startGossip(genesisValidatorsRoot(), false);
    final SignedInclusionList inclusionList = dataStructureUtil.randomSignedInclusionList();

    gossipForkSubscriptions.publishSignedInclusionList(inclusionList);

    verify(inclusionListTopicChannel).gossip(gossipEncoding.encode(inclusionList));
    verify(otherTopicChannel, never()).gossip(any());
  }

  private Bytes32 genesisValidatorsRoot() {
    return recentChainData.getGenesisData().orElseThrow().getGenesisValidatorsRoot();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private GossipForkSubscriptionsHezeBpo createGossipForkSubscriptionHezeBpo() {
    final OperationProcessor noopOperationProcessor = OperationProcessor.NOOP;

    return new GossipForkSubscriptionsHezeBpo(
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
        noopOperationProcessor,
        DebugDataDumper.NOOP,
        DasGossipLogger.NOOP,
        bpo,
        false,
        () -> false);
  }
}
