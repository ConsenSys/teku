/*
 * Copyright Consensys Software Inc., 2026
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Repeated block production requests for the same parent and slot. Before Heze every request
 * re-pins, as on master. From Heze an existing pin is kept while it is still being prepared or
 * already has a payloadId, so inclusion list payload attributes stay stable.
 */
@TestSpecContext(milestone = {SpecMilestone.BELLATRIX, SpecMilestone.HEZE})
class ForkChoiceNotifierBlockProductionRepinTest {

  private static final UInt64 GENESIS_TIME = UInt64.ZERO;

  private final InlineEventThread eventThread = new InlineEventThread();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);

  private Spec spec;
  private SpecMilestone specMilestone;
  private DataStructureUtil dataStructureUtil;
  private ForkChoiceNotifierImpl notifier;

  private final UInt64 blockSlot = UInt64.valueOf(2);
  private ForkChoiceNode parent;
  private Bytes32 parentExecutionHash;
  private ForkChoiceState forkChoiceState;
  private PayloadBuildingAttributes payloadBuildingAttributes;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    dataStructureUtil = specContext.getDataStructureUtil();

    parent = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
    parentExecutionHash = dataStructureUtil.randomBytes32();
    forkChoiceState = createForkChoiceState(dataStructureUtil.randomBytes32());
    payloadBuildingAttributes =
        new PayloadBuildingAttributes(
            UInt64.ONE,
            blockSlot,
            spec.computeTimeAtSlot(blockSlot, GENESIS_TIME),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            Optional.empty(),
            Optional.empty(),
            parent);

    when(recentChainData.getExecutionBlockHashForBlock(parent))
        .thenReturn(Optional.of(parentExecutionHash));
    when(recentChainData.getGenesisTime()).thenReturn(GENESIS_TIME);
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(blockSlot));
    doReturn(SafeFuture.completedFuture(Optional.of(payloadBuildingAttributes)))
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());

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
  }

  @TestTemplate
  void shouldRecoverWhenBlockProductionIsRequestedAgainAfterNoPayloadId() {
    final Bytes8 payloadId = dataStructureUtil.randomBytes8();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(forkChoiceUpdatedResult(ExecutionPayloadStatus.SYNCING, Optional.empty()))
        .thenReturn(forkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(payloadId)));

    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageContaining("Unable to obtain an executionPayloadContext");

    // block production is retried for the same parent and slot and must request a new payloadId
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    verify(executionLayerChannel, times(2))
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedWithOptionalContaining(
            new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes));
  }

  @TestTemplate
  void shouldReusePinWithPayloadIdOnlyFromHeze() {
    final Bytes8 initialPayloadId = dataStructureUtil.randomBytes8();
    final Bytes8 updatedPayloadId = dataStructureUtil.randomBytes8();
    final ForkChoiceState updatedForkChoiceState =
        createForkChoiceState(dataStructureUtil.randomBytes32());
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(
            forkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(initialPayloadId)));
    when(executionLayerChannel.engineForkChoiceUpdated(
            updatedForkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(
            forkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(updatedPayloadId)));

    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    // a repeated request for the same parent and slot with an updated safe block hash
    notifier.onForkChoiceUpdated(updatedForkChoiceState, Optional.of(blockSlot));

    if (isHezeActive()) {
      verify(executionLayerChannel, never())
          .engineForkChoiceUpdated(updatedForkChoiceState, Optional.of(payloadBuildingAttributes));
      assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
          .isCompletedWithOptionalContaining(
              new ExecutionPayloadContext(
                  initialPayloadId, forkChoiceState, payloadBuildingAttributes));
    } else {
      verify(executionLayerChannel)
          .engineForkChoiceUpdated(updatedForkChoiceState, Optional.of(payloadBuildingAttributes));
      assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
          .isCompletedWithOptionalContaining(
              new ExecutionPayloadContext(
                  updatedPayloadId, updatedForkChoiceState, payloadBuildingAttributes));
    }
  }

  @TestTemplate
  void shouldKeepInFlightInclusionListPinForRepeatedRequestOnlyFromHeze() {
    final Bytes8 initialPayloadId = dataStructureUtil.randomBytes8();
    final Bytes8 updatedPayloadId = dataStructureUtil.randomBytes8();
    final PayloadBuildingAttributes inclusionListPayloadBuildingAttributes =
        withInclusionListTransactions(payloadBuildingAttributes);
    final SafeFuture<ForkChoiceUpdatedResult> inclusionListResponse = new SafeFuture<>();
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(
            forkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(initialPayloadId)));
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(inclusionListPayloadBuildingAttributes)))
        .thenReturn(inclusionListResponse);

    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    doReturn(SafeFuture.completedFuture(Optional.of(inclusionListPayloadBuildingAttributes)))
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());
    final SafeFuture<Optional<ExecutionPayloadContext>> preparationFuture =
        notifier.preparePayloadAttributes(parent, blockSlot);
    final SafeFuture<Optional<ExecutionPayloadContext>> payloadContextFuture =
        notifier.getPayloadId(parent, blockSlot);
    assertThatSafeFuture(payloadContextFuture).isNotCompleted();

    // a repeated request while the inclusion list fcU is in flight
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    inclusionListResponse.complete(
        new ForkChoiceUpdatedResult(
            PayloadStatus.create(ExecutionPayloadStatus.VALID, Optional.empty(), Optional.empty()),
            Optional.of(updatedPayloadId)));

    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(
            updatedPayloadId, forkChoiceState, inclusionListPayloadBuildingAttributes);
    assertThatSafeFuture(preparationFuture)
        .isCompletedWithOptionalContaining(executionPayloadContext);
    if (isHezeActive()) {
      assertThatSafeFuture(payloadContextFuture)
          .isCompletedWithOptionalContaining(executionPayloadContext);
    } else {
      // before Heze the repeated request replaces the pin, as on master
      assertThatSafeFuture(payloadContextFuture)
          .isCompletedExceptionallyWith(IllegalStateException.class)
          .hasMessageContaining("was replaced");
    }
    // either way the inclusion list attributes are kept and requested only once
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedWithOptionalContaining(executionPayloadContext);
    verify(executionLayerChannel, times(1))
        .engineForkChoiceUpdated(
            forkChoiceState, Optional.of(inclusionListPayloadBuildingAttributes));
  }

  @TestTemplate
  void shouldRecoverWithInclusionListAttributesWhenBlockProductionIsRequestedAgain() {
    final Bytes8 initialPayloadId = dataStructureUtil.randomBytes8();
    final Bytes8 updatedPayloadId = dataStructureUtil.randomBytes8();
    final PayloadBuildingAttributes inclusionListPayloadBuildingAttributes =
        withInclusionListTransactions(payloadBuildingAttributes);
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(payloadBuildingAttributes)))
        .thenReturn(
            forkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(initialPayloadId)));
    when(executionLayerChannel.engineForkChoiceUpdated(
            forkChoiceState, Optional.of(inclusionListPayloadBuildingAttributes)))
        .thenReturn(forkChoiceUpdatedResult(ExecutionPayloadStatus.SYNCING, Optional.empty()))
        .thenReturn(
            forkChoiceUpdatedResult(ExecutionPayloadStatus.VALID, Optional.of(updatedPayloadId)));

    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));
    doReturn(SafeFuture.completedFuture(Optional.of(inclusionListPayloadBuildingAttributes)))
        .when(proposersDataManager)
        .calculatePayloadBuildingAttributes(any(), anyBoolean(), any(), anyBoolean());
    assertThatSafeFuture(notifier.preparePayloadAttributes(parent, blockSlot))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageContaining("Unable to obtain an executionPayloadContext");
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageContaining("Unable to obtain an executionPayloadContext");

    // the retried request must resend the inclusion list attributes, not drop them
    notifier.onForkChoiceUpdated(forkChoiceState, Optional.of(blockSlot));

    verify(executionLayerChannel, times(2))
        .engineForkChoiceUpdated(
            forkChoiceState, Optional.of(inclusionListPayloadBuildingAttributes));
    verify(executionLayerChannel, times(1))
        .engineForkChoiceUpdated(forkChoiceState, Optional.of(payloadBuildingAttributes));
    assertThatSafeFuture(notifier.getPayloadId(parent, blockSlot))
        .isCompletedWithOptionalContaining(
            new ExecutionPayloadContext(
                updatedPayloadId, forkChoiceState, inclusionListPayloadBuildingAttributes));
  }

  private boolean isHezeActive() {
    return specMilestone.isGreaterThanOrEqualTo(SpecMilestone.HEZE);
  }

  private ForkChoiceState createForkChoiceState(final Bytes32 safeExecutionBlockHash) {
    return new ForkChoiceState(
        parent,
        blockSlot.minusMinZero(1),
        UInt64.ONE,
        parentExecutionHash,
        safeExecutionBlockHash,
        Bytes32.ZERO,
        false);
  }

  private PayloadBuildingAttributes withInclusionListTransactions(
      final PayloadBuildingAttributes attributes) {
    return new PayloadBuildingAttributes(
        attributes.proposerIndex(),
        attributes.proposalSlot(),
        attributes.timestamp(),
        attributes.prevRandao(),
        attributes.feeRecipient(),
        attributes.targetGasLimit(),
        attributes.validatorRegistration(),
        attributes.withdrawals(),
        attributes.parentBeaconBlock(),
        List.of(Bytes.fromHexString("0x01"), Bytes.fromHexString("0x02")));
  }

  private static SafeFuture<ForkChoiceUpdatedResult> forkChoiceUpdatedResult(
      final ExecutionPayloadStatus status, final Optional<Bytes8> payloadId) {
    return SafeFuture.completedFuture(
        new ForkChoiceUpdatedResult(
            PayloadStatus.create(status, Optional.empty(), Optional.empty()), payloadId));
  }
}
