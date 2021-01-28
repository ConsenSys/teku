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
import tech.pegasys.teku.ssz.backing.ContainerViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.type.ViewType;

/** Autogenerated by tech.pegasys.teku.ssz.backing.ContainersGenerator */
public abstract class ContainerType2<
        C extends ContainerViewRead, V0 extends ViewRead, V1 extends ViewRead>
    extends ContainerViewType<C> {

  public static <C extends ContainerViewRead, V0 extends ViewRead, V1 extends ViewRead>
      ContainerType2<C, V0, V1> create(
          ViewType<V0> fieldType0,
          ViewType<V1> fieldType1,
          BiFunction<ContainerType2<C, V0, V1>, TreeNode, C> instanceCtor) {
    return new ContainerType2<>(fieldType0, fieldType1) {
      @Override
      public C createFromBackingNode(TreeNode node) {
        return instanceCtor.apply(this, node);
      }
    };
  }

  protected ContainerType2(ViewType<V0> fieldType0, ViewType<V1> fieldType1) {

    super(List.of(fieldType0, fieldType1));
  }

  @SuppressWarnings("unchecked")
  public ViewType<V0> getFieldType0() {
    return (ViewType<V0>) getChildType(0);
  }

  @SuppressWarnings("unchecked")
  public ViewType<V1> getFieldType1() {
    return (ViewType<V1>) getChildType(1);
  }
}
