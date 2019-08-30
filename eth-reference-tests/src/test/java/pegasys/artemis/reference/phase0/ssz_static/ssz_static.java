package pegasys.artemis.reference.phase0.ssz_static;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.CompactCommittee;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.hashtree.SigningRoot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BouncyCastleExtension.class)
public class ssz_static extends TestSuite {

  @ParameterizedTest(name = "{index} root of Merkleizable")
  @MethodSource({"readMessageSSZAttestationData", "readMessageSSZAttestationDataAndCustodyBit",
          "readMessageSSZAttesterSlashing", "readMessageSSZBeaconBlockBody", "readMessageSSZBeaconState",
          "readMessageSSZCheckpoint", "readMessageSSZCompactComitee", "readMessageSSZCrosslink",
          "readMessageSSZDeposit", "readMessageSSZEth1Data", "readMessageSSZFork", "readMessageSSZHistoricalBatch",
          "readMessageSSZPendingAttestation", "readMessageSSZProposerSlashing", "readMessageSSZValidator"})
  void sszCheckRootAndSigningRoot(
          Merkleizable merkleizable, Bytes32 root) {
    assertEquals(
            merkleizable.hash_tree_root(), root,
            merkleizable.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestationData() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/AttestationData/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, AttestationData.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestationDataAndCustodyBit() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/AttestationDataAndCustodyBit/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, AttestationDataAndCustodyBit.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttesterSlashing() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/AttesterSlashing/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, AttesterSlashing.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlockBody() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/BeaconBlockBody/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, BeaconBlockBody.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconState() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/BeaconState/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, BeaconState.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCheckpoint() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Checkpoint/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Checkpoint.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCompactComitee() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/CompactCommittee/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, CompactCommittee.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCrosslink() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Crosslink/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Crosslink.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZDeposit() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Deposit/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Deposit.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZEth1Data() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Eth1Data/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Eth1Data.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZFork() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Fork/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Fork.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZHistoricalBatch() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/HistoricalBatch/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, HistoricalBatch.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZPendingAttestation() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/PendingAttestation/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, PendingAttestation.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZProposerSlashing() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/ProposerSlashing/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, ProposerSlashing.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZValidator() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Validator/ssz_random");
    return sszStaticMerkleizableSetup(path, configPath, Validator.class);
  }

  @ParameterizedTest(name = "{index} check root and signing_root")
  @MethodSource({"readMessageSSZAttestation", "readMessageSSZBeaconBlock", "readMessageSSZBeaconBlockHeader", "readMessageSSZDepositData", "readMessageSSZIndexedAttestation", "readMessageSSZTransfer", "readMessageSSZVoluntaryExit"})
  void sszCheckRootAndSigningRoot(
          Merkleizable merkleizable, Bytes32 root, Bytes32 signing_root) {
    assertEquals(
            merkleizable.hash_tree_root(), root,
            merkleizable.getClass().getName() + " failed the root test");
    assertEquals(
            ((SigningRoot) merkleizable).signing_root("signature"), signing_root,
            merkleizable.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestation() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Attestation/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, Attestation.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlock() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/BeaconBlock/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, BeaconBlock.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlockHeader() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/BeaconBlockHeader/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, BeaconBlockHeader.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZDepositData() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/DepositData/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, DepositData.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZIndexedAttestation() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/IndexedAttestation/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, IndexedAttestation.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZTransfer() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/Transfer/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, Transfer.class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZVoluntaryExit() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/ssz_static/VoluntaryExit/ssz_random");
    return sszStaticRootSigningRootSetup(path, configPath, VoluntaryExit.class);
  }
}
