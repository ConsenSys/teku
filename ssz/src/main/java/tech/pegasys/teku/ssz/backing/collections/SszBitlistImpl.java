package tech.pegasys.teku.ssz.backing.collections;

import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas.SszBitListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class SszBitlistImpl extends SszListImpl<SszBit> implements SszBitlist {

  private final Bitlist value;

  public SszBitlistImpl(SszListSchema<SszBit, ?> schema, TreeNode backingNode) {
    super(schema, backingNode);
    value = SszUtils.getBitlist(this);
  }

  public SszBitlistImpl(SszListSchema<SszBit, ?> schema, Bitlist value) {
    super(schema, SszUtils.toSszBitList(value).getBackingNode());
    this.value = value;
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszBitlistSchema<SszBitlist> getSchema() {
    return (SszBitlistSchema<SszBitlist>) super.getSchema();
  }

  @Override
  public Bitlist toLegacy() {
    return value;
  }

  @Override
  public SszBitlist or(SszBitlist other) {
    return new SszBitlistImpl(getSchema(), value.or(other.toLegacy()));
  }

  @Override
  public boolean getBit(int i) {
    return value.getBit(i);
  }

  @Override
  public int getBitCount() {
    return value.getBitCount();
  }

  @Override
  public boolean intersects(SszBitlist other) {
    return value.intersects(other.toLegacy());
  }

  @Override
  public boolean isSuperSetOf(SszBitlist other) {
    return value.isSuperSetOf(other.toLegacy());
  }

  @Override
  public List<Integer> getAllSetBits() {
    return value.getAllSetBits();
  }

  @Override
  public IntStream streamAllSetBits() {
    return value.streamAllSetBits();
  }

  @Override
  public int getSize() {
    return value.getCurrentSize();
  }
}
