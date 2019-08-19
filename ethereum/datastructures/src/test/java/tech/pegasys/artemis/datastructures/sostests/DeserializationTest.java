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

package tech.pegasys.artemis.datastructures.sostests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public class DeserializationTest {
  @Test
  void isBeaconBlockBodyVariableTest() {
    // assertEquals(true,
    // SimpleOffsetSerializer.classReflectionInfo.get(BeaconBlockBody.class).isVariable());
  }

  @Test
  void BeaconBlockHeaderTest() {
    BeaconBlockHeader beaconBlockHeader = DataStructureUtil.randomBeaconBlockHeader(100);
    Bytes beaconBlockSerialized = SimpleOffsetSerializer.serialize(beaconBlockHeader);
    BeaconBlockHeader newBeaconBlockHeader =
        SimpleOffsetSerializer.deserialize(beaconBlockSerialized, BeaconBlockHeader.class);
    assertEquals(beaconBlockHeader, newBeaconBlockHeader);
  }

  @Test
  void isBeaconBlockVariableTest() {
    // assertEquals(true,
    // SimpleOffsetSerializer.classReflectionInfo.get(BeaconBlock.class).isVariable());
  }

  @Test
  void Eth1DataTest() {
    Eth1Data eth1Data = DataStructureUtil.randomEth1Data(100);
    Bytes eth1DataSerialized = SimpleOffsetSerializer.serialize(eth1Data);
    Eth1Data newEth1Data = SimpleOffsetSerializer.deserialize(eth1DataSerialized, Eth1Data.class);
    assertEquals(eth1Data, newEth1Data);
  }

  @Test
  void AttestationDataAndCustodyBitTest() {
    AttestationDataAndCustodyBit attestationDataAndCustodyBit =
        new AttestationDataAndCustodyBit(DataStructureUtil.randomAttestationData(100), true);
    Bytes attestationDataAndCustodyBitSerialized =
        SimpleOffsetSerializer.serialize(attestationDataAndCustodyBit);
    AttestationDataAndCustodyBit newObject =
        SimpleOffsetSerializer.deserialize(
            attestationDataAndCustodyBitSerialized, AttestationDataAndCustodyBit.class);
    assertEquals(attestationDataAndCustodyBit, newObject);
  }

  @Test
  void AttestationDataTest() {
    AttestationData attestationData = DataStructureUtil.randomAttestationData(100);
    assertEquals(
        attestationData,
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(attestationData), AttestationData.class));
  }

  @Test
  void AttestationTest() {
    /* THIS CLASS IS VARIABLE
    Attestation checkpoint = DataStructureUtil.randomAttestation(UnsignedLong.ONE);
    assertEquals(checkpoint, SimpleOffsetSerializer
            .deserialize(
                    SimpleOffsetSerializer.serialize(checkpoint),
                    Attestation.class));
                    */
  }

  @Test
  void isAttesterSlashingVariableTest() {
    // assertEquals(true,
    // SimpleOffsetSerializer.classReflectionInfo.get(AttesterSlashing.class).isVariable());
  }

  @Test
  void DepositDataTest() {
    DepositData depositData = DataStructureUtil.randomDepositData(100);
    assertEquals(
        depositData,
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(depositData), DepositData.class));
  }

  @Test
  void DepositTest() {
    Deposit deposit = DataStructureUtil.randomDeposit(100);
    Bytes serialized = SimpleOffsetSerializer.serialize(deposit);
    Deposit newDeposit = SimpleOffsetSerializer.deserialize(serialized, Deposit.class);
    // TODO
    // Fails due to Deposit having an extra index
    assertEquals(deposit, newDeposit);
  }

  @Test
  void isIndexedAttestationVariableTest() {
    // assertEquals(true,
    // SimpleOffsetSerializer.classReflectionInfo.get(IndexedAttestation.class).isVariable());
  }

  @Test
  void ProposerSlashingTest() {
    ProposerSlashing proposerSlashing = DataStructureUtil.randomProposerSlashing(100);
    assertEquals(
        proposerSlashing,
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(proposerSlashing), ProposerSlashing.class));
  }

  @Test
  void TransferTest() {
    Transfer transfer = DataStructureUtil.randomTransfer(100);
    assertEquals(
        transfer,
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(transfer), Transfer.class));
  }

  @Test
  void VoluntaryExitTest() {
    VoluntaryExit voluntaryExit = DataStructureUtil.randomVoluntaryExit(100);
    assertEquals(
        voluntaryExit,
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(voluntaryExit), VoluntaryExit.class));
  }

  @Test
  void isBeaconStateVariableTest() {
    // assertEquals(true,
    // SimpleOffsetSerializer.classReflectionInfo.get(BeaconState.class).isVariable());
  }

  @Test
  void isCheckpointVariableTest() {
    Checkpoint checkpoint = DataStructureUtil.randomCheckpoint(100);
    Bytes checkpointSerialized = SimpleOffsetSerializer.serialize(checkpoint);
    Checkpoint newCheckpoint =
        SimpleOffsetSerializer.deserialize(checkpointSerialized, Checkpoint.class);
    assertEquals(checkpoint, newCheckpoint);
  }

  @Test
  void isCompactCommitteVariableTest() {
    //  assertEquals(true,
    // SimpleOffsetSerializer.classReflectionInfo.get(CompactCommittee.class).isVariable());
  }

  @Test
  void CrosslinkTest() {
    Crosslink crosslink = DataStructureUtil.randomCrosslink(100);
    assertEquals(
        crosslink,
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(crosslink), Crosslink.class));
  }

  @Test
  void ForkTest() {
    Fork fork =
        new Fork(
            new Bytes4(int_to_bytes(2, 4)),
            new Bytes4(int_to_bytes(3, 4)),
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH));
    Fork newFork =
        SimpleOffsetSerializer.deserialize(SimpleOffsetSerializer.serialize(fork), Fork.class);
    assertEquals(fork, newFork);
  }

  @Test
  void HistoricalBatchTest() {
    SSZVector<Bytes32> block_roots = new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Bytes32.ZERO);
    SSZVector<Bytes32> state_roots = new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Bytes32.ZERO);
    IntStream.range(0, Constants.SLOTS_PER_HISTORICAL_ROOT)
        .forEach(
            i -> {
              block_roots.add(DataStructureUtil.randomBytes32(i));
              state_roots.add(DataStructureUtil.randomBytes32(i));
            });
    HistoricalBatch deposit = new HistoricalBatch(block_roots, state_roots);
    Bytes serialized = SimpleOffsetSerializer.serialize(deposit);
    HistoricalBatch newDeposit =
        SimpleOffsetSerializer.deserialize(serialized, HistoricalBatch.class);
    assertEquals(deposit, newDeposit);
  }

  @Test
  void isPendingAttestationVariableTest() {
    // assertEquals(false,
    // SimpleOffsetSerializer.classReflectionInfo.get(PendingAttestation.class).isVariable());
  }

  @Test
  void ValidatorTest() {
    Validator validator = DataStructureUtil.randomValidator(100);
    assertEquals(
        validator,
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(validator), Validator.class));
  }
}
