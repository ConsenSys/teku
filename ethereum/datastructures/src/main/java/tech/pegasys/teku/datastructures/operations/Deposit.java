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

package tech.pegasys.teku.datastructures.operations;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

public class Deposit extends Container2<Deposit, SszVector<SszBytes32>, DepositData> {

  public static class DepositType
      extends ContainerSchema2<Deposit, SszVector<SszBytes32>, DepositData> {

    public DepositType() {
      super(
          "Deposit",
          namedSchema(
              "proof",
              new SszVectorSchema<>(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1)),
          namedSchema("data", DepositData.TYPE));
    }

    public SszVectorSchema<SszBytes32> getProofType() {
      return (SszVectorSchema<SszBytes32>) getFieldSchema0();
    }

    @Override
    public Deposit createFromBackingNode(TreeNode node) {
      return new Deposit(this, node);
    }
  }

  public static final DepositType TYPE = new DepositType();

  private static final SSZVector<Bytes32> EMPTY_PROOF =
      SSZVector.createMutable(TYPE.getProofType().getLength(), Bytes32.ZERO);

  private Deposit(DepositType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Deposit(SSZVector<Bytes32> proof, DepositData data) {
    super(TYPE, SszUtils.toSszVector(TYPE.getProofType(), proof, SszBytes32::new), data);
  }

  public Deposit() {
    super(TYPE);
  }

  public Deposit(DepositData data) {
    this(EMPTY_PROOF, data);
  }

  public SSZVector<Bytes32> getProof() {
    return new SSZBackingVector<>(
        Bytes32.class, getField0(), SszBytes32::new, AbstractSszPrimitive::get);
  }

  public DepositData getData() {
    return getField1();
  }
}
