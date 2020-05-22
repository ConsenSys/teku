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

package tech.pegasys.teku.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.SAVED_FOR_FUTURE;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.core.results.AttestationProcessingResult.UNKNOWN_BLOCK;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;

import jdk.jfr.Event;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.results.AttestationProcessingResult;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.DelayableAttestation;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.ForkChoiceAttestationProcessor;
import tech.pegasys.teku.statetransition.events.attestation.ProcessedAggregateEvent;
import tech.pegasys.teku.statetransition.events.attestation.ProcessedAttestationEvent;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.EventSink;

class AttestationManagerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final EventBus eventBus = new EventBus();

  private final List<ProcessedAttestationEvent> processedAttestationEvents =
          EventSink.capture(eventBus, ProcessedAttestationEvent.class);

  private AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private  ForkChoiceAttestationProcessor attestationProcessor = mock(ForkChoiceAttestationProcessor.class);
  private final PendingPool<DelayableAttestation> pendingAttestations = spy(PendingPool.createForAttestations());
  private final FutureItems<DelayableAttestation> futureAttestations =
          spy(new FutureItems<>(DelayableAttestation::getEarliestSlotForForkChoiceProcessing));

  private final AttestationManager attestationManager =
          new AttestationManager(
                  eventBus, attestationProcessor, pendingAttestations, futureAttestations, attestationPool);


  @BeforeEach
  public void setup() {
    assertThat(attestationManager.start()).isCompleted();
  }

  @AfterEach
  public void cleanup() {
    assertThat(attestationManager.stop()).isCompleted();
  }

  @Test
  public void shouldProcessAttestationsThatAreReadyImmediately() {
    Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationProcessor.processAttestation(any())).thenReturn(SUCCESSFUL);
    attestationManager.onGossipedAttestation(attestation);

    verify(attestationProcessor).processAttestation(any());
    verify(attestationPool).add(eq(attestation));
    verifyNoInteractions(futureAttestations);
    verifyNoInteractions(pendingAttestations);
  }

  @Test
  public void shouldProcessAggregatesThatAreReadyImmediately() {
    final SignedAggregateAndProof aggregate =
            dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationProcessor.processAttestation(any())).thenReturn(SUCCESSFUL);
    attestationManager.onGossipedAggregateAndProof(aggregate);

    verify(attestationProcessor).processAttestation(any());
    verify(attestationPool).add(eq(aggregate.getMessage().getAggregate()));
    verifyNoInteractions(futureAttestations);
    verifyNoInteractions(pendingAttestations);
  }

  @Test
  public void shouldAddAttestationsThatHaveNotYetReachedTargetSlotToFutureItemsAndPool() {
    Attestation attestation = attestationFromSlot(100);
    IndexedAttestation randomIndexedAttestation = dataStructureUtil.randomIndexedAttestation();
    when(attestationProcessor.processAttestation(any()))
            .thenReturn(SAVED_FOR_FUTURE);
    attestationManager.onGossipedAttestation(attestation);

    ArgumentCaptor<DelayableAttestation> captor = ArgumentCaptor.forClass(DelayableAttestation.class);
    verify(attestationProcessor).processAttestation(captor.capture());
    captor.getValue().setIndexedAttestation(randomIndexedAttestation);
    verify(attestationPool).add(attestation);
    verify(futureAttestations).add(captor.getValue());
    verify(pendingAttestations, never()).add(any());

    // Shouldn't try to process the attestation until after it's slot.
    attestationManager.onSlot(UnsignedLong.valueOf(100));
    assertThat(futureAttestations.size()).isEqualTo(1);
    verify(attestationProcessor, never()).applyIndexedAttestationToForkChoice(any());

    attestationManager.onSlot(UnsignedLong.valueOf(101));
    verify(attestationProcessor).applyIndexedAttestationToForkChoice(eq(randomIndexedAttestation));
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
  }

  @Test
  public void shouldDeferProcessingForAttestationsThatAreMissingBlockDependencies() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    final Bytes32 requiredBlockRoot = block.getMessage().hash_tree_root();
    final Attestation attestation = attestationFromSlot(1, requiredBlockRoot);
    when(attestationProcessor.processAttestation(any()))
            .thenReturn(UNKNOWN_BLOCK)
            .thenReturn(SUCCESSFUL);
    attestationManager.onGossipedAttestation(attestation);

    ArgumentCaptor<DelayableAttestation> captor = ArgumentCaptor.forClass(DelayableAttestation.class);
    verify(attestationProcessor).processAttestation(captor.capture());
    assertThat(futureAttestations.size()).isZero();
    verify(pendingAttestations).add(captor.getValue());
    assertThat(pendingAttestations.size()).isEqualTo(1);

    // Slots progressing shouldn't cause the attestation to be processed
    attestationManager.onSlot(UnsignedLong.valueOf(100));
    verifyNoMoreInteractions(attestationProcessor);

    // Importing a different block shouldn't cause the attestation to be processed
    eventBus.post(new ImportedBlockEvent(dataStructureUtil.randomSignedBeaconBlock(2)));
    verifyNoMoreInteractions(attestationProcessor);

    eventBus.post(new ImportedBlockEvent(block));
    verify(attestationProcessor, times(2)).processAttestation(captor.getValue());
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    verify(attestationPool).add(eq(attestation));
  }

  @Test
  public void shouldNotPublishProcessedAttestationEventWhenAttestationIsInvalid() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationProcessor.processAttestation(any()))
            .thenReturn(AttestationProcessingResult.INVALID);
    attestationManager.onGossipedAttestation(attestation);

    ArgumentCaptor<DelayableAttestation> captor = ArgumentCaptor.forClass(DelayableAttestation.class);
    verify(attestationProcessor).processAttestation(captor.capture());
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  @Test
  public void shouldNotPublishProcessedAggregationEventWhenAttestationIsInvalid() {
    final SignedAggregateAndProof aggregateAndProof = dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationProcessor.processAttestation(any()))
            .thenReturn(AttestationProcessingResult.INVALID);
    attestationManager.onGossipedAggregateAndProof(aggregateAndProof);

    ArgumentCaptor<DelayableAttestation> captor = ArgumentCaptor.forClass(DelayableAttestation.class);
    verify(attestationProcessor).processAttestation(captor.capture());
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  private Attestation attestationFromSlot(final long slot) {
    return attestationFromSlot(slot, Bytes32.ZERO);
  }

  private Attestation attestationFromSlot(final long slot, final Bytes32 targetRoot) {
    return new Attestation(
            new Bitlist(1, 1),
            new AttestationData(
                    UnsignedLong.valueOf(slot),
                    UnsignedLong.ZERO,
                    Bytes32.ZERO,
                    new Checkpoint(UnsignedLong.ZERO, Bytes32.ZERO),
                    new Checkpoint(UnsignedLong.ZERO, targetRoot)),
            BLSSignature.empty());
  }
}
