package pegasys.artemis.reference.phase0.genesis;


import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@ExtendWith(BouncyCastleExtension.class)
public class initialization extends TestSuite {

  @ParameterizedTest(name = "{index} root of Merkleizable")
  @MethodSource({"genesisGenericInitializationSetup"})
  void genesisInitialization(
          BeaconState state, UnsignedLong eth1_timestamp, Bytes32 eth1_block_hash, List<Deposit> deposits) {
    //TODO
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> genesisGenericInitializationSetup() throws Exception {
    Path configPath = Paths.get("minimal", "phase0");
    Path path = Paths.get("/minimal/phase0/genesis/initialization/pyspec_tests/initialize_beacon_state_from_eth1");
    return genesisInitializationSetup(path, configPath);
  }
}
