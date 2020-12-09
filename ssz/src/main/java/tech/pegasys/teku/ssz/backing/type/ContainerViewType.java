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

package tech.pegasys.teku.ssz.backing.type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.teku.ssz.backing.BytesReader;
import tech.pegasys.teku.ssz.backing.ContainerViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;

public class ContainerViewType<C extends ContainerViewRead> implements CompositeViewType {

  private final List<ViewType> childrenTypes;
  private final BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor;
  private volatile TreeNode defaultTree;

  public ContainerViewType(
      List<ViewType> childrenTypes, BiFunction<ContainerViewType<C>, TreeNode, C> instanceCtor) {
    this.childrenTypes = childrenTypes;
    this.instanceCtor = instanceCtor;
  }

  @Override
  public C getDefault() {
    return createFromBackingNode(getDefaultTree());
  }

  @Override
  public TreeNode getDefaultTree() {
    if (defaultTree == null) {
      this.defaultTree = createDefaultTree();
    }
    return defaultTree;
  }

  private TreeNode createDefaultTree() {
    List<TreeNode> defaultChildren = new ArrayList<>((int) getMaxLength());
    for (int i = 0; i < getChildCount(); i++) {
      defaultChildren.add(getChildType(i).getDefault().getBackingNode());
    }
    return TreeUtil.createTree(defaultChildren);
  }

  @Override
  public ViewType getChildType(int index) {
    return childrenTypes.get(index);
  }

  @Override
  public C createFromBackingNode(TreeNode node) {
    return instanceCtor.apply(this, node);
  }

  @Override
  public long getMaxLength() {
    return childrenTypes.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ContainerViewType<?> that = (ContainerViewType<?>) o;
    return childrenTypes.equals(that.childrenTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(childrenTypes);
  }

  @Override
  public boolean isFixedSize() {
    for (int i = 0; i < getChildCount(); i++) {
      if (!getChildType(i).isFixedSize()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getFixedPartSize() {
    int size = 0;
    for (int i = 0; i < getChildCount(); i++) {
      ViewType childType = getChildType(i);
      size += childType.isFixedSize() ? childType.getFixedPartSize() : SSZ_LENGTH_SIZE;
    }
    return size;
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    int size = 0;
    for (int i = 0; i < getChildCount(); i++) {
      ViewType childType = getChildType(i);
      if (!childType.isFixedSize()) {
        size += childType.getVariablePartSize(node.get(getGeneralizedIndex(i)));
      }
    }
    return size;
  }

  private int getChildCount() {
    return (int) getMaxLength();
  }

  @Override
  public int sszSerialize(TreeNode node, Consumer<Bytes> writer) {
    int variableChildOffset = getFixedPartSize();
    int[] variableSizes = new int[getChildCount()];
    for (int i = 0; i < getChildCount(); i++) {
      TreeNode childSubtree = node.get(getGeneralizedIndex(i));
      ViewType childType = getChildType(i);
      if (childType.isFixedSize()) {
        int size = childType.sszSerialize(childSubtree, writer);
        assert size == childType.getFixedPartSize();
      } else {
        writer.accept(SSZType.lengthToBytes(variableChildOffset));
        int childSize = childType.getSszSize(childSubtree);
        variableSizes[i] = childSize;
        variableChildOffset += childSize;
      }
    }
    for (int i = 0; i < getMaxLength(); i++) {
      ViewType childType = getChildType(i);
      if (!childType.isFixedSize()) {
        TreeNode childSubtree = node.get(getGeneralizedIndex(i));
        int size = childType.sszSerialize(childSubtree, writer);
        assert size == variableSizes[i];
      }
    }
    return variableChildOffset;
  }

  @Override
  public TreeNode sszDeserializeTree(BytesReader reader) {
    Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>();
    Queue<Integer> variableChildrenOffsets = new ArrayDeque<>();
    int originalAvailableBytes = reader.getAvailableBytes();
    for (int i = 0; i < getChildCount(); i++) {
      ViewType childType = getChildType(i);
      if (childType.isFixedSize()) {
        TreeNode childNode = childType.sszDeserializeTree(reader.slice(childType.getFixedPartSize()));
        fixedChildrenSubtrees.add(childNode);
      } else {
        int childOffset = SSZType.bytesToLength(reader.read(SSZ_LENGTH_SIZE));
        variableChildrenOffsets.add(childOffset);
      }
    }
    int readBytes = reader.getAvailableBytes() - originalAvailableBytes;

    List<TreeNode> childrenSubtrees;
    if (variableChildrenOffsets.isEmpty()) {
      childrenSubtrees = new ArrayList<>(fixedChildrenSubtrees);
    } else {
      Integer curVariableChildOffset = variableChildrenOffsets.remove();
      if (readBytes != curVariableChildOffset) {
        throw new SSZException("Invalid SSZ");
      }
      childrenSubtrees = new ArrayList<>(getChildCount());
      for (int i = 0; i < getChildCount(); i++) {
        ViewType childType = getChildType(i);
        if (childType.isFixedSize()) {
          childrenSubtrees.add(fixedChildrenSubtrees.remove());
        } else {
          Integer nextVariableChildOffset = variableChildrenOffsets.poll();
          BytesReader childReader = nextVariableChildOffset == null ? reader :
              reader.slice(nextVariableChildOffset - curVariableChildOffset);
          TreeNode childNode = childType.sszDeserializeTree(childReader);
          if (childReader.getAvailableBytes() > 0) {
            throw new SSZException("Invalid SSZ");
          }
          childrenSubtrees.add(childNode);
          curVariableChildOffset = nextVariableChildOffset;
        }
      }
    }

    return TreeUtil.createTree(childrenSubtrees);
  }
}
