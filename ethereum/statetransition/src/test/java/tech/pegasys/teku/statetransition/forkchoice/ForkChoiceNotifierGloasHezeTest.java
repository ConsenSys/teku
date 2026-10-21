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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * {@link ForkChoiceNotifierImpl} on a real Gloas/Heze chain, with a real {@link
 * ProposersDataManager} reading inclusion lists from a real {@link InclusionListStore}. Inclusion
 * list transactions must only reach the execution layer from Heze.
 */
@TestSpecContext(milestone = {SpecMilestone.GLOAS, SpecMilestone.HEZE})
class ForkChoiceNotifierGloasHezeTest {

  private static final Bytes IL_TX_1 = Bytes.fromHexString("0x01");
  private static final Bytes IL_TX_2 = Bytes.fromHexString("0x02");
  private static final Bytes IL_TX_3 = Bytes.fromHexString("0x03");
  private static final Bytes IL_TX_OTHER_DEPENDENT_ROOT = Bytes.fromHexString("0x09");
  private static final Bytes IL_TX_OTHER_SLOT = Bytes.fromHexString("0x0a");

  private final InlineEventThread eventThread = new InlineEventThread();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);
  private final InclusionListStore inclusionListStore = new InclusionListStore(4);
  private final ProposerPreferencesManager proposerPreferencesManager =
      mock(ProposerPreferencesManager.class);

  private Spec spec;
  private SpecMilestone specMilestone;
  private DataStructureUtil dataStructureUtil;
  private RecentChainData recentChainData;
  private ProposersDataManager proposersDataManager;
  private ForkChoiceNotifierImpl notifier;

  private Bytes32 genesisRoot;
  private ForkChoiceNode parent;
  private ForkChoiceState forkChoiceState;
  private UInt64 blockSlot;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    dataStructureUtil = specContext.getDataStructureUtil();

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    recentChainData = storageSystem.recentChainData();
    genesisRoot = storageSystem.chainUpdater().initializeGenesis().getRoot();
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(UInt64.ONE);
    storageSystem.chainUpdater().updateBestBlock(head);
    // build on the FULL node of the head, as the payload for the head has been revealed and saved
    recentChainData.updateHead(ForkChoiceNode.createFull(head.getRoot()), head.getSlot());

    parent = ForkChoiceNode.createFull(head.getRoot());
    blockSlot = head.getSlot().increment();
    forkChoiceState = getForkChoiceState();
    assertThat(forkChoiceState.headBlock()).isEqualTo(parent);
    assertThat(forkChoiceState.headExecutionBlockHash()).isNotEqualTo(Bytes32.ZERO);
    assertThat(recentChainData.getCurrentSlot()).contains(head.getSlot());

    proposersDataManager =
        spy(
            new ProposersDataManager(
                eventThread,
                spec,
                new StubMetricsSystem(),
                executionLayerChannel,
                recentChainData,
                Optional.of(dataStructureUtil.randomEth1Address()),
                false,
                proposerPreferencesManager,
                inclusionListStore));
    notifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayerChannel,
            recentChainData,
            proposersDataManager,
            false);
    notifier.onSyncingStatusChanged(true);

    when(executionLayerChannel.engineForkChoiceUpdated(any(), any()))
        .thenAnswer(
            __ ->
                SafeFuture.completedFuture(
                    forkChoiceUpdatedResult(Optional.of(dataStructureUtil.randomBytes8()))));
  }

  @TestTemplate
  void onForkChoiceUpdated_shouldSendInclusionListTransactionsForPreparedProposerOnlyFromHeze() {
    storeInclusionLists();
    prepareProposer(blockSlot);

    notifier.onForkChoiceUpdated(forkChoiceState, Optional.empty());

    final PayloadBuildingAttributes sentAttributes = getLastSentPayloadBuildingAttributes();
    assertThat(sentAttributes.proposalSlot()).isEqualTo(blockSlot);
    assertThat(sentAttributes.parentBeaconBlock()).isEqualTo(parent);
    assertExpectedInclusionListTransactions(sentAttributes);
  }

  @TestTemplate
  void preparePayloadAttributes_shouldSendInclusionListTransactionsOnlyFromHeze() {
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.empty());
    storeInclusionLists();

    final SafeFuture<Optional<ExecutionPayloadContext>> preparationFuture =
        notifier.preparePayloadAttributes(parent, blockSlot);

    final ExecutionPayloadContext executionPayloadContext =
        safeJoin(preparationFuture).orElseThrow();
    final PayloadBuildingAttributes attributes =
        executionPayloadContext.getPayloadBuildingAttributes();
    assertThat(executionPayloadContext.getForkChoiceState()).isEqualTo(forkChoiceState);
    assertThat(attributes.proposalSlot()).isEqualTo(blockSlot);
    assertThat(attributes.parentBeaconBlock()).isEqualTo(parent);
    assertThat(attributes.timestamp()).isEqualTo(recentChainData.computeTimeAtSlot(blockSlot));
    assertExpectedInclusionListTransactions(attributes);
    // the exact attributes reached the execution layer
    verify(executionLayerChannel).engineForkChoiceUpdated(forkChoiceState, Optional.of(attributes));
  }

  @TestTemplate
  void getPayloadId_shouldUseInclusionListAttributesRefreshedAfterPinningOnlyFromHeze() {
    // block production is pinned before any inclusion list is known
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    final PayloadBuildingAttributes initialAttributes = getLastSentPayloadBuildingAttributes();
    assertThat(initialAttributes.inclusionListTransactions()).isEmpty();

    // inclusion lists arrive and the payload attributes are refreshed for the pinned production
    storeInclusionLists();
    final ExecutionPayloadContext preparedContext =
        safeJoin(notifier.preparePayloadAttributes(parent, blockSlot)).orElseThrow();

    final ExecutionPayloadContext payloadContext =
        safeJoin(notifier.getPayloadId(parent, blockSlot)).orElseThrow();
    assertThat(payloadContext).isEqualTo(preparedContext);
    assertExpectedInclusionListTransactions(payloadContext.getPayloadBuildingAttributes());
    assertThat(getLastSentPayloadBuildingAttributes())
        .isEqualTo(payloadContext.getPayloadBuildingAttributes());
    if (isHezeActive()) {
      assertThat(payloadContext.getPayloadBuildingAttributes()).isNotEqualTo(initialAttributes);
    } else {
      assertThat(payloadContext.getPayloadBuildingAttributes()).isEqualTo(initialAttributes);
    }
  }

  @TestTemplate
  void onPayloadAttestationsDue_shouldKeepInclusionListAttributesOnlyFromHeze() {
    prepareProposer(blockSlot);
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.empty());
    storeInclusionLists();
    final PayloadBuildingAttributes preparedAttributes =
        safeJoin(notifier.preparePayloadAttributes(parent, blockSlot))
            .orElseThrow()
            .getPayloadBuildingAttributes();
    clearInvocations(proposersDataManager);
    // a later inclusion list would change recalculated attributes
    storeInclusionList(blockSlot.decrement(), genesisRoot, 3, Bytes.fromHexString("0x04"));

    notifier.onPayloadAttestationsDue(blockSlot.decrement());
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.empty());

    if (isHezeActive()) {
      // the inclusion list attributes stay stable until block production
      assertThat(getLastSentPayloadBuildingAttributes()).isEqualTo(preparedAttributes);
      verify(proposersDataManager, never())
          .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), eq(false));
    } else {
      // without inclusion lists, payload attestations due refreshes the attributes as usual
      verify(proposersDataManager, atLeastOnce())
          .calculatePayloadBuildingAttributes(eq(blockSlot), anyBoolean(), any(), eq(false));
      assertThat(getLastSentPayloadBuildingAttributes().inclusionListTransactions()).isEmpty();
    }
  }

  @TestTemplate
  void preparePayloadAttributes_shouldFailPendingAndPinnedPreparationWhenCalculationFails() {
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    final SafeFuture<Optional<ExecutionPayloadContext>> originalPayloadContext =
        notifier.getPayloadId(parent, blockSlot);
    assertThatSafeFuture(originalPayloadContext).isCompleted();
    clearInvocations(executionLayerChannel);

    doReturn(SafeFuture.failedFuture(new RuntimeException("calculation failed")))
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(eq(blockSlot), anyBoolean(), any(), eq(true));
    final SafeFuture<Optional<ExecutionPayloadContext>> preparationFuture =
        notifier.preparePayloadAttributes(parent, blockSlot);

    assertThatSafeFuture(preparationFuture)
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessage(
            "Payload attributes preparation for slot " + blockSlot + " failed: calculation failed")
        .hasRootCauseExactlyInstanceOf(RuntimeException.class)
        .hasRootCauseMessage("calculation failed");
    // the pinned block production being refreshed fails with the same error
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageStartingWith(
            "Payload attributes preparation for slot " + blockSlot + " failed: ")
        .hasMessageEndingWith("calculation failed");
    verify(executionLayerChannel, never()).engineForkChoiceUpdated(any(), any());

    // once the calculation recovers, a new preparation for the same parent and slot succeeds
    doCallRealMethod()
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());
    storeInclusionLists();
    final ExecutionPayloadContext recoveredContext =
        safeJoin(notifier.preparePayloadAttributes(parent, blockSlot)).orElseThrow();
    assertExpectedInclusionListTransactions(recoveredContext.getPayloadBuildingAttributes());
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedWithOptionalContaining(recoveredContext);
  }

  @TestTemplate
  void blockProduction_shouldRecoverAfterFailedPayloadAttributesPreparation() {
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    doReturn(SafeFuture.failedFuture(new RuntimeException("calculation failed")))
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(eq(blockSlot), anyBoolean(), any(), eq(true));
    assertThatSafeFuture(notifier.preparePayloadAttributes(parent, blockSlot))
        .isCompletedExceptionallyWith(IllegalStateException.class);
    doCallRealMethod()
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

    // a retried block production request must not reuse the failed pin
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    final ExecutionPayloadContext payloadContext =
        safeJoin(notifier.getPayloadId(parent, blockSlot)).orElseThrow();
    assertThat(payloadContext.getPayloadBuildingAttributes().proposalSlot()).isEqualTo(blockSlot);
    assertThat(payloadContext.getPayloadBuildingAttributes().parentBeaconBlock()).isEqualTo(parent);
  }

  @TestTemplate
  void preparePayloadAttributes_shouldReuseInFlightPreparationForSameParentAndSlot() {
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.empty());
    final SafeFuture<Optional<PayloadBuildingAttributes>> deferredAttributes = new SafeFuture<>();
    doReturn(deferredAttributes)
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(eq(blockSlot), anyBoolean(), any(), eq(true));

    final SafeFuture<Optional<ExecutionPayloadContext>> firstPreparation =
        notifier.preparePayloadAttributes(parent, blockSlot);
    final SafeFuture<Optional<ExecutionPayloadContext>> secondPreparation =
        notifier.preparePayloadAttributes(parent, blockSlot);

    assertThatSafeFuture(firstPreparation).isNotCompleted();
    assertThatSafeFuture(secondPreparation).isNotCompleted();
    verify(proposersDataManager, times(1))
        .calculatePayloadBuildingAttributes(eq(blockSlot), anyBoolean(), any(), eq(true));

    final PayloadBuildingAttributes attributes = calculateRealAttributes(blockSlot);
    completeOnEventThread(deferredAttributes, Optional.of(attributes));

    final ExecutionPayloadContext executionPayloadContext =
        safeJoin(firstPreparation).orElseThrow();
    assertThat(executionPayloadContext.getPayloadBuildingAttributes()).isEqualTo(attributes);
    assertThatSafeFuture(secondPreparation)
        .isCompletedWithOptionalContaining(executionPayloadContext);
  }

  @TestTemplate
  void preparePayloadAttributes_shouldFailPendingPreparationWhenReplacedByNewerPreparation() {
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.empty());
    final UInt64 nextBlockSlot = blockSlot.increment();
    final SafeFuture<Optional<PayloadBuildingAttributes>> staleAttributes = new SafeFuture<>();
    doReturn(staleAttributes)
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(eq(blockSlot), anyBoolean(), any(), eq(true));
    final SafeFuture<Optional<ExecutionPayloadContext>> stalePreparation =
        notifier.preparePayloadAttributes(parent, blockSlot);
    assertThatSafeFuture(stalePreparation).isNotCompleted();

    storeInclusionLists(nextBlockSlot.decrement());
    final SafeFuture<Optional<ExecutionPayloadContext>> newerPreparation =
        notifier.preparePayloadAttributes(parent, nextBlockSlot);

    assertThatSafeFuture(stalePreparation)
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessage(
            "Payload attributes preparation for slot "
                + blockSlot
                + " was replaced by a newer inclusion list payload preparation");
    final ExecutionPayloadContext newerContext = safeJoin(newerPreparation).orElseThrow();
    assertThat(newerContext.getPayloadBuildingAttributes().proposalSlot()).isEqualTo(nextBlockSlot);
    assertExpectedInclusionListTransactions(newerContext.getPayloadBuildingAttributes());

    // the stale calculation completing late must not replace the newer attributes
    final PayloadBuildingAttributes lateStaleAttributes = calculateRealAttributes(blockSlot);
    completeOnEventThread(staleAttributes, Optional.of(lateStaleAttributes));
    verify(executionLayerChannel, never())
        .engineForkChoiceUpdated(any(), eq(Optional.of(lateStaleAttributes)));
    assertThat(getLastSentPayloadBuildingAttributes())
        .isEqualTo(newerContext.getPayloadBuildingAttributes());
  }

  @TestTemplate
  void
      onForkChoiceUpdated_shouldFailPendingPreparationWhenReplacedByPinnedProductionForOtherSlot() {
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.empty());
    final UInt64 nextBlockSlot = blockSlot.increment();
    final SafeFuture<Optional<PayloadBuildingAttributes>> pendingAttributes = new SafeFuture<>();
    doReturn(pendingAttributes)
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(eq(blockSlot), anyBoolean(), any(), eq(true));
    final SafeFuture<Optional<ExecutionPayloadContext>> pendingPreparation =
        notifier.preparePayloadAttributes(parent, blockSlot);

    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(nextBlockSlot));

    assertThatSafeFuture(pendingPreparation)
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessage(
            "Payload attributes preparation for slot "
                + blockSlot
                + " was replaced by pinned block production for slot "
                + nextBlockSlot);
    assertThatSafeFuture(notifier.getPayloadId(parent, nextBlockSlot)).isCompleted();
  }

  @TestTemplate
  void onPayloadAttestationsDue_shouldClearPinnedBlockProductionOnlyOnceExpired() {
    final SafeFuture<ForkChoiceUpdatedResult> engineResponse = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(any(), any())).thenReturn(engineResponse);
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    final SafeFuture<Optional<ExecutionPayloadContext>> payloadContext =
        notifier.getPayloadId(parent, blockSlot);
    assertThatSafeFuture(payloadContext).isNotCompleted();

    // not expired while still at the pinned slot
    notifier.onPayloadAttestationsDue(blockSlot);
    assertThatSafeFuture(payloadContext).isNotCompleted();

    // from Gloas, attestations due does not prepare the next slot, so it does not expire the pin
    notifier.onAttestationsDue(blockSlot.increment());
    assertThatSafeFuture(payloadContext).isNotCompleted();

    notifier.onPayloadAttestationsDue(blockSlot.increment());
    assertThatSafeFuture(payloadContext)
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessage(
            "Pinned block production for slot "
                + blockSlot
                + " was cleared because attestations were due for slot "
                + blockSlot.increment());
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageContaining("No pinned block production preparation");
  }

  @TestTemplate
  void preparePayloadAttributes_shouldClearExpiredPinAndItsPendingPreparation() {
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    final SafeFuture<Optional<PayloadBuildingAttributes>> pendingAttributes = new SafeFuture<>();
    doReturn(pendingAttributes)
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(eq(blockSlot), anyBoolean(), any(), eq(true));
    final SafeFuture<Optional<ExecutionPayloadContext>> pendingPreparation =
        notifier.preparePayloadAttributes(parent, blockSlot);
    final SafeFuture<Optional<ExecutionPayloadContext>> pinnedPayloadContext =
        notifier.getPayloadId(parent, blockSlot);
    assertThatSafeFuture(pendingPreparation).isNotCompleted();
    assertThatSafeFuture(pinnedPayloadContext).isNotCompleted();

    final UInt64 nextBlockSlot = blockSlot.increment();
    storeInclusionLists(blockSlot);
    final SafeFuture<Optional<ExecutionPayloadContext>> nextPreparation =
        notifier.preparePayloadAttributes(parent, nextBlockSlot);

    final String expectedReason =
        " was cleared because payload attributes were requested for slot " + nextBlockSlot;
    assertThatSafeFuture(pinnedPayloadContext)
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessage("Pinned block production for slot " + blockSlot + expectedReason);
    assertThatSafeFuture(pendingPreparation)
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessage("Payload attributes preparation for slot " + blockSlot + expectedReason);
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageContaining("No pinned block production preparation");

    final ExecutionPayloadContext nextContext = safeJoin(nextPreparation).orElseThrow();
    assertThat(nextContext.getPayloadBuildingAttributes().proposalSlot()).isEqualTo(nextBlockSlot);
    assertExpectedInclusionListTransactions(nextContext.getPayloadBuildingAttributes());
  }

  @TestTemplate
  void preparePayloadAttributes_shouldNotClearPinForSameSlot() {
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    storeInclusionLists();

    final ExecutionPayloadContext preparedContext =
        safeJoin(notifier.preparePayloadAttributes(parent, blockSlot)).orElseThrow();

    // the pin is refreshed rather than cleared, so block production can still use it
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedWithOptionalContaining(preparedContext);
  }

  private boolean isHezeActive() {
    return specMilestone.isGreaterThanOrEqualTo(SpecMilestone.HEZE);
  }

  private void assertExpectedInclusionListTransactions(final PayloadBuildingAttributes attributes) {
    if (isHezeActive()) {
      assertThat(attributes.inclusionListTransactions())
          .containsExactlyInAnyOrder(IL_TX_1, IL_TX_2, IL_TX_3);
    } else {
      assertThat(attributes.inclusionListTransactions()).isEmpty();
    }
  }

  private PayloadBuildingAttributes getLastSentPayloadBuildingAttributes() {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Optional<PayloadBuildingAttributes>> captor =
        ArgumentCaptor.forClass(Optional.class);
    verify(executionLayerChannel, atLeastOnce())
        .engineForkChoiceUpdated(eq(forkChoiceState), captor.capture());
    return captor.getValue().orElseThrow();
  }

  private void storeInclusionLists() {
    storeInclusionLists(blockSlot.decrement());
  }

  /**
   * Stores inclusion lists for the proposal following {@code inclusionListSlot}: two lists with
   * overlapping transactions for the dependent root of the chain, plus lists that must be ignored
   * because they are for another dependent root or another slot.
   */
  private void storeInclusionLists(final UInt64 inclusionListSlot) {
    // early in the chain the shuffling dependent root is the genesis block
    storeInclusionList(inclusionListSlot, genesisRoot, 0, IL_TX_1, IL_TX_2);
    storeInclusionList(inclusionListSlot, genesisRoot, 1, IL_TX_2, IL_TX_3);
    storeInclusionList(
        inclusionListSlot, dataStructureUtil.randomBytes32(), 2, IL_TX_OTHER_DEPENDENT_ROOT);
    storeInclusionList(inclusionListSlot.increment(), genesisRoot, 2, IL_TX_OTHER_SLOT);
  }

  private void storeInclusionList(
      final UInt64 slot,
      final Bytes32 dependentRoot,
      final int validatorIndex,
      final Bytes... transactions) {
    // inclusion lists only exist from Heze, use Heze schemas to populate the store on any fork
    final SchemaDefinitionsHeze schemaDefinitions =
        SchemaDefinitionsHeze.required(
            TestSpecFactory.createMinimalHeze().getGenesisSchemaDefinitions());
    final InclusionList inclusionList =
        schemaDefinitions
            .getInclusionListSchema()
            .create(
                slot,
                UInt64.valueOf(validatorIndex),
                dependentRoot,
                List.of(transactions).stream()
                    .map(
                        schemaDefinitions.getInclusionListSchema().getTransactionSchema()
                            ::fromBytes)
                    .toList());
    inclusionListStore.processInclusionList(
        schemaDefinitions
            .getSignedInclusionListSchema()
            .create(inclusionList, dataStructureUtil.randomSignature()),
        true);
  }

  private void prepareProposer(final UInt64 slot) {
    final BeaconState state =
        safeJoin(recentChainData.retrieveBlockState(new SlotAndBlockRoot(slot, parent.blockRoot())))
            .orElseThrow();
    final UInt64 proposerIndex = UInt64.valueOf(spec.getBeaconProposerIndex(state, slot));
    final Eth1Address feeRecipient = dataStructureUtil.randomEth1Address();
    // From Gloas, fee recipients come only from accepted signed proposer preferences
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final SignedProposerPreferences preferences =
        schemaDefinitions
            .getSignedProposerPreferencesSchema()
            .create(
                schemaDefinitions
                    .getProposerPreferencesSchema()
                    .create(
                        dataStructureUtil.randomBytes32(),
                        slot,
                        proposerIndex,
                        feeRecipient,
                        dataStructureUtil.randomUInt64()),
                dataStructureUtil.randomSignature());
    when(proposerPreferencesManager.getProposerPreferences(eq(slot), any()))
        .thenReturn(Optional.of(preferences.getMessage()));
    proposersDataManager.updatePreparedProposersFromProposerPreferences(
        List.of(preferences), recentChainData.getHeadSlot());
  }

  private PayloadBuildingAttributes calculateRealAttributes(final UInt64 slot) {
    final ForkChoiceUpdateData forkChoiceUpdateData =
        new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), Optional.empty());
    final SafeFuture<Optional<PayloadBuildingAttributes>> result = new SafeFuture<>();
    eventThread.execute(
        () ->
            new ProposersDataManager(
                    eventThread,
                    spec,
                    new StubMetricsSystem(),
                    executionLayerChannel,
                    recentChainData,
                    Optional.of(dataStructureUtil.randomEth1Address()),
                    false,
                    inclusionListStore)
                .calculatePayloadBuildingAttributes(slot, true, forkChoiceUpdateData, true)
                .propagateTo(result));
    return safeJoin(result).orElseThrow();
  }

  private <T> void completeOnEventThread(final SafeFuture<T> future, final T value) {
    assertThatSafeFuture(eventThread.execute(() -> future.complete(value)))
        .isCompletedWithValue(true);
  }

  private ForkChoiceState getForkChoiceState() {
    final ForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getUpdatableForkChoiceStrategy().orElseThrow();
    return forkChoiceStrategy.getForkChoiceState(
        recentChainData.getChainHead(),
        recentChainData.getCurrentEpoch().orElseThrow(),
        recentChainData.getJustifiedCheckpoint().orElseThrow(),
        recentChainData.getFinalizedCheckpoint().orElseThrow(),
        Optional.empty());
  }

  private static ForkChoiceUpdatedResult forkChoiceUpdatedResult(final Optional<Bytes8> payloadId) {
    return new ForkChoiceUpdatedResult(
        PayloadStatus.create(ExecutionPayloadStatus.VALID, Optional.empty(), Optional.empty()),
        payloadId);
  }
}
