/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing.containers;

import java.util.List;
import java.util.function.BiFunction;
import tech.pegasys.teku.ssz.backing.SszContainer;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

/** Autogenerated by tech.pegasys.teku.ssz.backing.ContainersGenerator */
public abstract class ContainerSchema1<C extends SszContainer, V0 extends SszData>
    extends AbstractSszContainerSchema<C> {

  public static <C extends SszContainer, V0 extends SszData> ContainerSchema1<C, V0> create(
      SszSchema<V0> fieldSchema0, BiFunction<ContainerSchema1<C, V0>, TreeNode, C> instanceCtor) {
    return new ContainerSchema1<>(fieldSchema0) {
      @Override
      public C createFromBackingNode(TreeNode node) {
        return instanceCtor.apply(this, node);
      }
    };
  }

  protected ContainerSchema1(SszSchema<V0> fieldSchema0) {

    super(List.of(fieldSchema0));
  }

  protected ContainerSchema1(String containerName, NamedSchema<V0> fieldNamedSchema0) {

    super(containerName, List.of(fieldNamedSchema0));
  }

  @SuppressWarnings("unchecked")
  public SszSchema<V0> getFieldSchema0() {
    return (SszSchema<V0>) getChildSchema(0);
  }
}
