/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.statetransition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static tech.pegasys.artemis.statetransition.AttestationGenerator.withNewAttesterBits;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTATIONS;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

class BlockAttestationsPoolTest {

  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(12);
  private final ChainStorageClient storageClient =
      spy(new ChainStorageClient(mock(EventBus.class)));
  private final AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
  private BlockAttestationsPool pool;

  @BeforeEach
  void setup() {
    BeaconChainUtil.initializeStorage(storageClient, validatorKeys);
    pool = new BlockAttestationsPool();
  }

  @Test
  void unprocessedAggregate_SingleAttester_ShouldBeIgnored() throws Exception {
    final Attestation attestation = attestationGenerator.validAttestation(storageClient);
    pool.addUnprocessedAggregateAttestationToQueue(attestation);
    assertFalse(pool.aggregateAttesationsQueue.contains(attestation));
    assertFalse(
        pool.unprocessedAttestationsBitlist.containsValue(attestation.getAggregation_bits()));
  }

  @Test
  @SuppressWarnings("UnusedVariable")
  void unprocessedAggregate_NewData() throws Exception {
    Attestation attestation = AttestationGenerator.aggregateAttestation(10);
    pool.addUnprocessedAggregateAttestationToQueue(attestation);
    assertTrue(pool.aggregateAttesationsQueue.contains(attestation));
    assertTrue(
        pool.unprocessedAttestationsBitlist.containsValue(attestation.getAggregation_bits()));
  }

  @Test
  void unprocessedAggregate_OldData_DifferentBitlist_BitlistUpdated() throws Exception {
    Attestation attestation = AttestationGenerator.aggregateAttestation(10);
    Attestation newAttestation = withNewAttesterBits(new Attestation(attestation), 1);

    pool.addUnprocessedAggregateAttestationToQueue(attestation);
    pool.addUnprocessedAggregateAttestationToQueue(newAttestation);
    Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
    Bitlist bitlist = pool.unprocessedAttestationsBitlist.get(attestationDataHash);
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (bitlist.getBit(i) != attestation.getAggregation_bits().getBit(i)
          && bitlist.getBit(i) != newAttestation.getAggregation_bits().getBit(i)) {
        fail();
      }
    }
    assert (pool.aggregateAttesationsQueue.size() == 2);
  }

  @Test
  void unprocessedAggregate_OldData_SameBitlist_ShouldBeIgnored() throws Exception {
    Attestation attestation = AttestationGenerator.aggregateAttestation(10);
    pool.addUnprocessedAggregateAttestationToQueue(attestation);
    Attestation newAttestation = new Attestation(attestation);
    pool.addUnprocessedAggregateAttestationToQueue(newAttestation);
    assert (pool.aggregateAttesationsQueue.size() == 1);
  }

  @Test
  void processedAggregate_NewData_SetBits() throws Exception {
    Attestation attestation = AttestationGenerator.aggregateAttestation(10);
    pool.addAggregateAttestationProcessedInBlock(attestation);
    Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
    Bitlist bitlist = pool.processedAttestationsBitlist.get(attestationDataHash);
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (bitlist.getBit(i) != attestation.getAggregation_bits().getBit(i)) {
        fail();
      }
    }
  }

  @Test
  void processedAggregate_OldData_DifferentBitlist_SetBits() throws Exception {
    Attestation attestation = AttestationGenerator.aggregateAttestation(10);
    pool.addAggregateAttestationProcessedInBlock(attestation);
    Bytes32 attestationDataHash = attestation.getData().hash_tree_root();
    Bitlist bitlist = pool.processedAttestationsBitlist.get(attestationDataHash);

    Attestation newAttestation = withNewAttesterBits(new Attestation(attestation), 1);
    pool.addAggregateAttestationProcessedInBlock(attestation);
    for (int i = 0; i < attestation.getAggregation_bits().getCurrentSize(); i++) {
      if (bitlist.getBit(i) != attestation.getAggregation_bits().getBit(i)
          && bitlist.getBit(i) != newAttestation.getAggregation_bits().getBit(i)) {
        fail();
      }
    }
  }

  @Test
  void getAggregatedAttestations_DoesNotReturnAttestationWithNoNewBits() throws Exception {
    Attestation attestation = AttestationGenerator.aggregateAttestation(10);
    pool.addAggregateAttestationProcessedInBlock(attestation);
    pool.addUnprocessedAggregateAttestationToQueue(attestation);

    assertEquals(pool.getAggregatedAttestationsForBlockAtSlot(UnsignedLong.MAX_VALUE).size(), 0);
  }

  @Test
  void getAggregatedAttestations_DoesNotReturnAttestationsMoreThanMaxAttestations()
      throws Exception {
    for (int i = 0; i < MAX_ATTESTATIONS + 1; i++) {
      Attestation attestation = DataStructureUtil.randomAttestation(i);
      attestation.setData(new AttestationData(UnsignedLong.valueOf(i), attestation.getData()));
      pool.addUnprocessedAggregateAttestationToQueue(attestation);
    }

    assertEquals(
        pool.getAggregatedAttestationsForBlockAtSlot(UnsignedLong.MAX_VALUE).size(),
        MAX_ATTESTATIONS);
  }

  @Test
  void getAggregatedAttestations_DoesNotReturnAttestationsWithSlotsLessThanGivenSlot()
      throws Exception {
    int SLOT = 10;
    for (int i = 0; i < SLOT; i++) {
      Attestation attestation = DataStructureUtil.randomAttestation(i);
      attestation.setData(new AttestationData(UnsignedLong.valueOf(i), attestation.getData()));
      pool.addUnprocessedAggregateAttestationToQueue(attestation);
    }

    UnsignedLong CUTOFF_SLOT = UnsignedLong.valueOf(5);
    pool.getAggregatedAttestationsForBlockAtSlot(CUTOFF_SLOT)
        .forEach(
            attestation -> {
              if (attestation.getData().getSlot().compareTo(CUTOFF_SLOT) > 0) {
                fail();
              }
            });
  }
}
