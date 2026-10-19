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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class Eth2P2PNetworkBuilderTest {

  private static final UInt64 HEZE_FORK_EPOCH = UInt64.ONE;
  private static final UInt64 BPO_EPOCH = UInt64.valueOf(2);

  private final Spec spec =
      TestSpecFactory.createMinimalHeze(
          builder ->
              builder
                  .hezeForkEpoch(HEZE_FORK_EPOCH)
                  .fuluBuilder(
                      fuluBuilder ->
                          fuluBuilder.blobSchedule(List.of(new BlobScheduleEntry(BPO_EPOCH, 64)))));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
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
            storageSystem.recentChainData().getForkDigest(BPO_EPOCH),
            GossipTopicName.INCLUSION_LIST,
            gossipEncoding);

    when(otherTopicChannel.gossip(any())).thenReturn(SafeFuture.COMPLETE);
    when(inclusionListTopicChannel.gossip(any())).thenReturn(SafeFuture.COMPLETE);
    when(discoveryNetwork.subscribe(any(), any())).thenReturn(otherTopicChannel);
    when(discoveryNetwork.subscribe(eq(inclusionListTopic), any()))
        .thenReturn(inclusionListTopicChannel);
  }

  @Test
  void shouldPublishInclusionListsAfterBpoForkDuringHeze() {
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(BPO_EPOCH))
        .isEqualTo(SpecMilestone.HEZE);
    assertThat(spec.getBpoForks()).anyMatch(bpo -> bpo.epoch().equals(BPO_EPOCH));
    final GossipForkManager gossipForkManager = createGossipForkManager();

    // The inclusion list topic is only subscribed while the head is not optimistic
    gossipForkManager.onOptimisticHeadChanged(false);
    gossipForkManager.configureGossipForEpoch(BPO_EPOCH);
    final SignedInclusionList inclusionList =
        inclusionListAtSlot(spec.computeStartSlotAtEpoch(BPO_EPOCH));
    gossipForkManager.publishInclusionList(inclusionList);

    verify(discoveryNetwork).subscribe(eq(inclusionListTopic), any());
    verify(inclusionListTopicChannel).gossip(gossipEncoding.encode(inclusionList));
  }

  private SignedInclusionList inclusionListAtSlot(final UInt64 slot) {
    final SchemaDefinitionsHeze schemaDefinitions =
        SchemaDefinitionsHeze.required(spec.atSlot(slot).getSchemaDefinitions());
    final InclusionList randomInclusionList = dataStructureUtil.randomInclusionList();
    final InclusionList inclusionList =
        schemaDefinitions
            .getInclusionListSchema()
            .create(
                slot,
                randomInclusionList.getValidatorIndex(),
                randomInclusionList.getDependentRoot(),
                randomInclusionList.getTransactions().asList());
    return schemaDefinitions
        .getSignedInclusionListSchema()
        .create(inclusionList, dataStructureUtil.randomSignature());
  }

  private GossipForkManager createGossipForkManager() {
    return Eth2P2PNetworkBuilder.create()
        .specProvider(spec)
        .config(P2PConfig.builder().specProvider(spec).build())
        .combinedChainDataClient(storageSystem.combinedChainDataClient())
        .asyncRunner(new StubAsyncRunner())
        .metricsSystem(new StubMetricsSystem())
        .p2pDebugDataDumper(DebugDataDumper.NOOP)
        .gossipDasLogger(DasGossipLogger.NOOP)
        .isSuperNodeSupplier(() -> false)
        .gossipedInclusionListProcessor(OperationProcessor.noop())
        .buildGossipForkManager(gossipEncoding, discoveryNetwork);
  }
}
