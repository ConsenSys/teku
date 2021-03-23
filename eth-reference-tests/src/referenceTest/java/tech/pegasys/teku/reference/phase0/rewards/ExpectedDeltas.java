package tech.pegasys.teku.reference.phase0.rewards;

import tech.pegasys.teku.spec.logic.common.statetransition.epoch.Deltas;
import tech.pegasys.teku.ssz.collections.SszUInt64List;
import tech.pegasys.teku.ssz.containers.Container2;
import tech.pegasys.teku.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class ExpectedDeltas extends Container2<ExpectedDeltas, SszUInt64List, SszUInt64List> {
  public static final DeltasSchema SSZ_SCHEMA = new DeltasSchema();

  protected ExpectedDeltas(
      final ContainerSchema2<ExpectedDeltas, SszUInt64List, SszUInt64List> schema,
      final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public Deltas getDeltas() {
    final SszUInt64List rewards = getField0();
    final SszUInt64List penalties = getField1();
    final Deltas deltas = new Deltas(rewards.size());
    for (int i = 0; i < rewards.size(); i++) {
      deltas.getDelta(i).reward(rewards.get(i).get());
      deltas.getDelta(i).penalize(penalties.get(i).get());
    }
    return deltas;
  }

  public static class DeltasSchema
      extends ContainerSchema2<ExpectedDeltas, SszUInt64List, SszUInt64List> {

    private DeltasSchema() {
      super(
          "Deltas",
          NamedSchema.of("rewards", SszUInt64ListSchema.create(Integer.MAX_VALUE)),
          NamedSchema.of("penalties", SszUInt64ListSchema.create(Integer.MAX_VALUE)));
    }

    @Override
    public ExpectedDeltas createFromBackingNode(final TreeNode node) {
      return new ExpectedDeltas(this, node);
    }
  }
}
