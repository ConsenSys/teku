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

package tech.pegasys.teku.networking.eth2.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.pubsub.NoPeersForOutboundMessageException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * Uses a spec that starts in Gloas and forks to Heze at epoch 1 so that the fork boundary handling
 * of the inclusion list topic can be exercised.
 */
public class InclusionListGossipManagerTest {

  private static final UInt64 HEZE_FORK_EPOCH = UInt64.ONE;

  private final Spec spec = TestSpecFactory.createMinimalWithHezeForkEpoch(HEZE_FORK_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final TopicChannel topicChannel = mock(TopicChannel.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @SuppressWarnings("unchecked")
  private final OperationProcessor<SignedInclusionList> processor = mock(OperationProcessor.class);

  private final UInt64 hezeSlot = spec.computeStartSlotAtEpoch(HEZE_FORK_EPOCH).plus(1);

  private ForkInfo forkInfo;
  private Bytes4 forkDigest;
  private String expectedTopic;
  private InclusionListGossipManager gossipManager;

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    forkInfo = recentChainData.getForkInfo(HEZE_FORK_EPOCH).orElseThrow();
    forkDigest = recentChainData.getForkDigest(HEZE_FORK_EPOCH);
    expectedTopic =
        GossipTopics.getTopic(forkDigest, GossipTopicName.INCLUSION_LIST, gossipEncoding);

    when(topicChannel.gossip(any())).thenReturn(SafeFuture.COMPLETE);
    when(gossipNetwork.subscribe(eq(expectedTopic), any())).thenReturn(topicChannel);

    gossipManager = createGossipManager(forkInfo, forkDigest);
  }

  @Test
  void shouldUseHezeFork() {
    assertThat(spec.atEpoch(forkInfo.getFork().getEpoch()).getMilestone())
        .isEqualTo(SpecMilestone.HEZE);
  }

  @Test
  void shouldUseInclusionListTopicForForkDigest() {
    assertThat(expectedTopic)
        .isEqualTo("/eth2/" + forkDigest.toUnprefixedHexString() + "/inclusion_list/ssz_snappy");
    assertThat(gossipManager.getTopicHandler().getTopic()).isEqualTo(expectedTopic);
  }

  @Test
  void shouldNotBeEnabledDuringOptimisticSync() {
    assertThat(gossipManager.isEnabledDuringOptimisticSync()).isFalse();
  }

  @Test
  void shouldSubscribeToInclusionListTopic() {
    gossipManager.subscribe();

    verify(gossipNetwork).subscribe(eq(expectedTopic), eq(gossipManager.getTopicHandler()));
  }

  @Test
  void shouldOnlySubscribeOnceWhenSubscribeCalledTwice() {
    gossipManager.subscribe();
    gossipManager.subscribe();

    verify(gossipNetwork).subscribe(eq(expectedTopic), any());
  }

  @Test
  void shouldBeSafeToUnsubscribeWhenNotSubscribed() {
    gossipManager.unsubscribe();

    verifyNoInteractions(gossipNetwork, topicChannel);
  }

  @Test
  void shouldCloseTopicChannelWhenUnsubscribing() {
    gossipManager.subscribe();
    gossipManager.unsubscribe();

    verify(topicChannel).close();
  }

  @Test
  void shouldPublishEncodedInclusionList() {
    gossipManager.subscribe();
    final SignedInclusionList inclusionList = createSignedInclusionList(hezeSlot);

    gossipManager.publish(inclusionList);

    verify(topicChannel).gossip(gossipEncoding.encode(inclusionList));
  }

  @Test
  void shouldNotPublishBeforeSubscribing() {
    gossipManager.publish(createSignedInclusionList(hezeSlot));

    verifyNoInteractions(gossipNetwork, topicChannel);
  }

  @Test
  void shouldNotPublishAfterUnsubscribing() {
    gossipManager.subscribe();
    gossipManager.unsubscribe();

    gossipManager.publish(createSignedInclusionList(hezeSlot));

    verify(topicChannel, never()).gossip(any());
  }

  @Test
  void shouldLogInclusionListSlotWhenPublishingFails() {
    gossipManager.subscribe();
    final SignedInclusionList inclusionList = createSignedInclusionList(hezeSlot);
    when(topicChannel.gossip(any()))
        .thenReturn(
            SafeFuture.failedFuture(new NoPeersForOutboundMessageException("no peers here")));

    try (final LogCaptor logCaptor = LogCaptor.forClass(GossipFailureLogger.class)) {
      gossipManager.publish(inclusionList);

      assertThat(logCaptor.getWarnLogs())
          .containsExactly(
              "Failed to publish inclusion_list(s) for slot " + hezeSlot + "; no peers here");
    }
  }

  @Test
  void shouldAcceptValidInclusionList() {
    final SignedInclusionList inclusionList = createSignedInclusionList(hezeSlot);
    when(processor.process(inclusionList, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<ValidationResult> result =
        handleIncoming(gossipEncoding.encode(inclusionList));

    assertThat(result).isCompletedWithValue(ValidationResult.Valid);
    verify(processor).process(inclusionList, Optional.empty());
  }

  @Test
  void shouldIgnoreInclusionListIgnoredByProcessor() {
    final SignedInclusionList inclusionList = createSignedInclusionList(hezeSlot);
    when(processor.process(inclusionList, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));

    final SafeFuture<ValidationResult> result =
        handleIncoming(gossipEncoding.encode(inclusionList));

    assertThat(result).isCompletedWithValue(ValidationResult.Ignore);
  }

  @Test
  void shouldRejectInclusionListRejectedByProcessor() {
    final SignedInclusionList inclusionList = createSignedInclusionList(hezeSlot);
    when(processor.process(inclusionList, Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("bad IL")));

    final SafeFuture<ValidationResult> result =
        handleIncoming(gossipEncoding.encode(inclusionList));

    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
  }

  @Test
  void shouldRejectInvalidSsz() {
    final SafeFuture<ValidationResult> result = handleIncoming(Bytes.fromHexString("0x1234"));

    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verifyNoInteractions(processor);
  }

  @Test
  void shouldRejectInclusionListForSlotBeforeHezeWithoutProcessing() {
    // the milestone is derived from the inclusion list slot, so a Gloas slot is the wrong fork
    final UInt64 gloasSlot = spec.computeStartSlotAtEpoch(HEZE_FORK_EPOCH).minus(1);
    assertThat(spec.atSlot(gloasSlot).getMilestone()).isEqualTo(SpecMilestone.GLOAS);
    final SignedInclusionList inclusionList = createSignedInclusionList(gloasSlot);

    final SafeFuture<ValidationResult> result =
        handleIncoming(gossipEncoding.encode(inclusionList));

    assertThat(result).isCompletedWithValue(ValidationResult.Invalid);
    verifyNoInteractions(processor);
  }

  @Test
  void shouldNotBeCreatableForGloasFork() {
    final ForkInfo gloasForkInfo = recentChainData.getForkInfo(UInt64.ZERO).orElseThrow();
    assertThat(spec.atEpoch(gloasForkInfo.getFork().getEpoch()).getMilestone())
        .isEqualTo(SpecMilestone.GLOAS);

    assertThatThrownBy(
            () -> createGossipManager(gloasForkInfo, recentChainData.getForkDigest(UInt64.ZERO)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private SafeFuture<ValidationResult> handleIncoming(final Bytes serialized) {
    final Eth2TopicHandler<SignedInclusionList> topicHandler = gossipManager.getTopicHandler();
    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    return result;
  }

  private InclusionListGossipManager createGossipManager(
      final ForkInfo forkInfo, final Bytes4 forkDigest) {
    return new InclusionListGossipManager(
        spec,
        recentChainData,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        spec.getNetworkingConfig(),
        DebugDataDumper.NOOP);
  }

  private SignedInclusionList createSignedInclusionList(final UInt64 slot) {
    // always use the Heze schemas so an inclusion list can be built for a pre-Heze slot too
    final SchemaDefinitionsHeze schemaDefinitions =
        SchemaDefinitionsHeze.required(spec.atEpoch(HEZE_FORK_EPOCH).getSchemaDefinitions());
    final InclusionListSchema inclusionListSchema = schemaDefinitions.getInclusionListSchema();
    final Transaction transaction =
        inclusionListSchema.getTransactionSchema().fromBytes(dataStructureUtil.randomBytes(64));
    final InclusionList inclusionList =
        inclusionListSchema.create(
            slot,
            dataStructureUtil.randomValidatorIndex(),
            dataStructureUtil.randomBytes32(),
            List.of(transaction));
    return schemaDefinitions
        .getSignedInclusionListSchema()
        .create(inclusionList, dataStructureUtil.randomSignature());
  }
}
