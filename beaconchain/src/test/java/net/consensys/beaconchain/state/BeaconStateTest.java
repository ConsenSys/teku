package net.consensys.beaconchain.state;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.beaconchain.ethereum.core.Hash;
import net.consensys.beaconchain.util.bytes.Bytes3;
import net.consensys.beaconchain.util.bytes.BytesValue;

import org.junit.Test;

public class BeaconStateTest {

  private Hash hashSrc() {
    BytesValue bytes = Bytes3.wrap(new byte[]{(byte) 1, (byte) 256, (byte) 65656});
    return Hash.hash(bytes);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentIntToBytes3() {
    BeaconState.BeaconStateHelperFunctions.intToBytes3(-1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentsBytes3ToInt() {
    Bytes3 bytes = Bytes3.wrap(new byte[]{(byte) 1, (byte) 256, (byte) 65656});
    BeaconState.BeaconStateHelperFunctions.bytes3ToInt(bytes, -1);
  }

  @Test
  public void convertIntToBytes3() {
    Bytes3 expected = Bytes3.wrap(new byte[]{(byte) 1, (byte) 256, (byte) 65656});
    Bytes3 actual = BeaconState.BeaconStateHelperFunctions.intToBytes3(65656);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void convertBytes3ToInt() {
    int expected = 65656;
    Bytes3 bytes = Bytes3.wrap(new byte[]{(byte) 1, (byte) 256, (byte) 65656});
    int actual = BeaconState.BeaconStateHelperFunctions.bytes3ToInt(bytes, 0);
    assertThat(actual).isEqualTo(expected);
  }

}
