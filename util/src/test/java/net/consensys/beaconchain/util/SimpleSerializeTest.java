package net.consensys.beaconchain.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import net.consensys.beaconchain.util.bytes.Bytes32;

import org.junit.Test;



public class SimpleSerializeTest {

  @Test
  public void serializeBytes32Test() {
    Bytes32 bytes32 = Bytes32.wrap("55555555555555555555555555555555".getBytes(UTF_8));
    byte[] serialized = SimpleSerialize.serialize(bytes32);
    byte[] comparator = "55555555555555555555555555555555".getBytes(UTF_8);

    assertArrayEquals(serialized, comparator);
  }
  @Test
  public void deserializeBytes32Test() {
    Bytes32 comparator = Bytes32.wrap("55555555555555555555555555555555".getBytes(UTF_8));
    Bytes32 deserialized = SimpleSerialize.deserialize("55555555555555555555555555555555".getBytes(UTF_8));

    assertTrue(deserialized.equals(comparator));
  }
}
