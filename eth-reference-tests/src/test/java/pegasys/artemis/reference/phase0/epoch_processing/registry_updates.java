package pegasys.artemis.reference.phase0.epoch_processing;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.statetransition.util.EpochProcessorUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BouncyCastleExtension.class)
public class registry_updates extends TestSuite {
  private static final Path configPath = Paths.get("mainnet");

  @ParameterizedTest(name = "{index}. process registry updates pre={0} -> post={1}")
  @MethodSource("processRegistryUpdates")
  void processRegistryUpdates(BeaconState pre, BeaconState post) throws Exception {
    EpochProcessorUtil.process_registry_updates(pre);
    assertEquals(pre, post);
  }

  @MustBeClosed
  static Stream<Arguments> processRegistryUpdates() throws Exception {
    Path path =
            Paths.get("mainnet", "phase0", "epoch_processing", "registry_updates", "pyspec_tests");
    return epochProcessingSetup(path, configPath);
  }
}
