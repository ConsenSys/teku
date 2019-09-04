package tech.pegasys.artemis.storage.serializers;

import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class SSZSerializer<T extends SimpleOffsetSerializable> implements Serializer<T> {
  private final Class<T> targetClass;

  public SSZSerializer(final Class<T> targetClass) {
    this.targetClass = targetClass;
  }

  @Override
  public void serialize(final DataOutput2 out, final T value) throws IOException {
    out.writeChars(SimpleOffsetSerializer.serialize(value).toHexString());
  }

  @Override
  public T deserialize(final DataInput2 input, final int available) throws IOException {
    return SimpleOffsetSerializer.deserialize(Bytes.fromHexString(input.readLine()), targetClass);
  }
}
