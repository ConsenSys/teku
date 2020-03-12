package tech.pegasys.artemis.benchmarks.util.backing;

import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.config.Constants;

@Fork(0)
@State(Scope.Thread)
public class BeaconStateBenchmark {

  private static BeaconState beaconState = DataStructureUtil.randomBeaconState(0, 32 * 1024);

  public BeaconStateBenchmark() {
    Constants.setConstants("mainnet");
  }

  @Benchmark
  @Warmup(iterations = 2, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void iterateValidators(Blackhole bh) {
    for (Validator validator : beaconState.getValidators()) {
      bh.consume(validator);
    }
  }

  public static void main(String[] args) throws Exception {
    BeaconStateBenchmark b = new BeaconStateBenchmark();
    b.iterateValidators(new Blackhole(""));
    b.iterateValidators(new Blackhole(""));
  }

  @Test
  void aaa() {
  }

  static int i = 0;

  @Benchmark
  @Warmup(iterations = 2, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void iterateValidatorsWithMethods(Blackhole bh) {
    for (Validator validator : beaconState.getValidators()) {
      bh.consume(validator.isSlashed());
//      bh.consume(validator.getPubkey());
      bh.consume(validator.getEffective_balance());
      bh.consume(validator.getActivation_epoch());
      bh.consume(validator.getExit_epoch());
      bh.consume(validator.getWithdrawable_epoch());
    }
  }

  @Benchmark
  @Warmup(iterations = 2, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void iterateBalances(Blackhole bh) {
    for (UnsignedLong balance : beaconState.getBalances()) {
      bh.consume(balance);
    }
  }
}
