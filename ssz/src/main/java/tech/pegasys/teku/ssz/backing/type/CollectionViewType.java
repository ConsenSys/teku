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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.SszNodeTemplate;
import tech.pegasys.teku.ssz.backing.tree.SszSuperNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.backing.type.TypeHints.SszSuperNodeHint;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;

/** Type of homogeneous collections (like List and Vector) */
public abstract class CollectionViewType implements CompositeViewType {

  private final long maxLength;
  private final ViewType elementType;
  private final TypeHints hints;
  protected final Supplier<SszNodeTemplate> elementSszSupernodeTemplate =
      Suppliers.memoize(() -> SszNodeTemplate.createFromType(getElementType()));
  private volatile TreeNode defaultTree;

  protected CollectionViewType(long maxLength, ViewType elementType, TypeHints hints) {
    this.maxLength = maxLength;
    this.elementType = elementType;
    this.hints = hints;
  }

  protected abstract TreeNode createDefaultTree();

  @Override
  public TreeNode getDefaultTree() {
    if (defaultTree == null) {
      this.defaultTree = createDefaultTree();
    }
    return defaultTree;
  }

  @Override
  public long getMaxLength() {
    return maxLength;
  }

  public ViewType getElementType() {
    return elementType;
  }

  @Override
  public ViewType getChildType(int index) {
    return getElementType();
  }

  @Override
  public int getElementsPerChunk() {
    return 256 / getElementType().getBitsSize();
  }

  protected int getVariablePartSize(TreeNode vectorNode, int length) {
    if (isFixedSize()) {
      return 0;
    } else {
      int size = 0;
      for (int i = 0; i < length; i++) {
        size += getElementType().getSszSize(vectorNode.get(getGeneralizedIndex(i)));
      }
      return size;
    }
  }

  /**
   * Serializes {@code elementsCount} from the content of this collection
   *
   * @param vectorNode for a {@link VectorViewType} type - the node itself, for a {@link
   *     ListViewType} - the left sibling node of list size node
   */
  protected int sszSerializeVector(TreeNode vectorNode, Consumer<Bytes> writer, int elementsCount) {
    if (getElementType().isFixedSize()) {
      return sszSerializeFixedVectorFast(vectorNode, writer, elementsCount);
    } else {
      return sszSerializeVariableVector(vectorNode, writer, elementsCount);
    }
  }

  private int sszSerializeFixedVectorFast(
      TreeNode vectorNode, Consumer<Bytes> writer, int elementsCount) {
    if (elementsCount == 0) {
      return 0;
    }
    int nodesCount = getChunks(elementsCount);
    int[] bytesCnt = new int[1];
    TreeUtil.iterateLeavesData(
        vectorNode,
        getGeneralizedIndex(0),
        getGeneralizedIndex(nodesCount - 1),
        leafData -> {
          writer.accept(leafData);
          bytesCnt[0] += leafData.size();
        });
    return bytesCnt[0];
  }

  private int sszSerializeVariableVector(
      TreeNode vectorNode, Consumer<Bytes> writer, int elementsCount) {
    ViewType elementType = getElementType();
    int variableOffset = SSZ_LENGTH_SIZE * elementsCount;
    for (int i = 0; i < elementsCount; i++) {
      TreeNode childSubtree = vectorNode.get(getGeneralizedIndex(i));
      int childSize = elementType.getSszSize(childSubtree);
      writer.accept(SSZType.lengthToBytes(variableOffset));
      variableOffset += childSize;
    }
    for (int i = 0; i < elementsCount; i++) {
      TreeNode childSubtree = vectorNode.get(getGeneralizedIndex(i));
      elementType.sszSerialize(childSubtree, writer);
    }
    return variableOffset;
  }

  static class DeserializedData {

    private final TreeNode dataTree;
    private final int childrenCount;
    private final Optional<Byte> lastSszByte;

    public DeserializedData(TreeNode dataTree, int childrenCount) {
      this(dataTree, childrenCount, Optional.empty());
    }

    public DeserializedData(TreeNode dataTree, int childrenCount, Optional<Byte> lastSszByte) {
      this.dataTree = dataTree;
      this.childrenCount = childrenCount;
      this.lastSszByte = lastSszByte;
    }

    public TreeNode getDataTree() {
      return dataTree;
    }

    public int getChildrenCount() {
      return childrenCount;
    }

    public Optional<Byte> getLastSszByte() {
      return lastSszByte;
    }
  }

  protected DeserializedData sszDeserializeVector(SszReader reader) {
    checkSsz(reader.getAvailableBytes() >= getFixedPartSize(), "Ssz is too large");
    if (getElementType().isFixedSize()) {
      Optional<SszSuperNodeHint> sszSuperNodeHint = getHints().getHint(SszSuperNodeHint.class);
      if (sszSuperNodeHint.isPresent()) {
        return sszDeserializeSupernode(reader, sszSuperNodeHint.get().getDepth());
      } else {
        return sszDeserializeFixed(reader);
      }
    } else {
      return sszDeserializeVariable(reader);
    }
  }

  private DeserializedData sszDeserializeSupernode(SszReader reader, int supernodeDepth) {
    SszNodeTemplate template = elementSszSupernodeTemplate.get();
    int sszSize = reader.getAvailableBytes();
    if (sszSize % template.getSszLength() != 0) {
      throw new SSZDeserializeException("Ssz length is not multiple of element length");
    }
    int elementsCount = sszSize / template.getSszLength();
    List<SszSuperNode> sszNodes =
        chunks(sszSize, (1 << supernodeDepth) * template.getSszLength())
            .mapToObj(reader::read)
            .map(bb -> new SszSuperNode(supernodeDepth, template, bb))
            .collect(Collectors.toList());
    TreeNode tree =
        TreeUtil.createTree(
            sszNodes,
            new SszSuperNode(supernodeDepth, template, Bytes.EMPTY),
            treeDepth() - supernodeDepth);
    return new DeserializedData(tree, elementsCount);
  }

  private DeserializedData sszDeserializeFixed(SszReader reader) {
    int bytesSize = reader.getAvailableBytes();
    if (getElementType() instanceof BasicViewType) {
      checkSsz(bytesSize % getElementType().getFixedPartSize() == 0, "");
      List<LeafNode> childNodes =
          chunks(bytesSize, LeafNode.MAX_BYTE_SIZE)
              .mapToObj(reader::read)
              .map(LeafNode::create)
              .collect(Collectors.toList());

      Optional<Byte> lastByte;
      if (childNodes.isEmpty()) {
        lastByte = Optional.empty();
      } else {
        Bytes lastNodeData = childNodes.get(childNodes.size() - 1).getData();
        lastByte = Optional.of(lastNodeData.get(lastNodeData.size() - 1));
      }
      return new DeserializedData(
          TreeUtil.createTree(childNodes, treeDepth()),
          bytesSize / getElementType().getFixedPartSize(),
          lastByte);
    } else {
      checkSsz(
          bytesSize % getElementType().getFixedPartSize() == 0,
          "Ssz length is not multiple of element length");
      int elementsCount = bytesSize / getElementType().getFixedPartSize();
      List<TreeNode> childNodes =
          Stream.generate(
                  () -> {
                    try (SszReader sszReader = reader.slice(getElementType().getFixedPartSize())) {
                      return getElementType().sszDeserializeTree(sszReader);
                    }
                  })
              .limit(elementsCount)
              .collect(Collectors.toList());
      return new DeserializedData(TreeUtil.createTree(childNodes, treeDepth()), elementsCount);
    }
  }

  private DeserializedData sszDeserializeVariable(SszReader reader) {
    final int endVarOffset = reader.getAvailableBytes();
    final int elementsCount;
    final List<TreeNode> childNodes;
    if (endVarOffset == 0) {
      // empty list
      elementsCount = 0;
      childNodes = Collections.emptyList();
    } else {
      int varElementOffset = SSZType.bytesToLength(reader.read(SSZ_LENGTH_SIZE));
      checkSsz(varElementOffset % SSZ_LENGTH_SIZE == 0, "Invalid first element offset");
      elementsCount = varElementOffset / SSZ_LENGTH_SIZE;
      int[] elementSizes = new int[elementsCount];
      for (int i = 1; i < elementsCount; i++) {
        int offset = SSZType.bytesToLength(reader.read(SSZ_LENGTH_SIZE));
        elementSizes[i - 1] = offset - varElementOffset;
        varElementOffset = offset;
      }
      elementSizes[elementsCount - 1] = endVarOffset - varElementOffset;

      childNodes =
          Arrays.stream(elementSizes)
              .mapToObj(
                  size -> {
                    try (SszReader sszReader = reader.slice(size)) {
                      return getElementType().sszDeserializeTree(sszReader);
                    }
                  })
              .collect(Collectors.toList());
    }
    return new DeserializedData(TreeUtil.createTree(childNodes, treeDepth()), elementsCount);
  }

  private static void checkSsz(boolean condition, String error) {
    if (!condition) {
      throw new SSZDeserializeException(error);
    }
  }

  protected static IntStream chunks(int totalSize, int chunkSize) {
    return IntStream.concat(
        IntStream.generate(() -> chunkSize).limit(totalSize / chunkSize),
        IntStream.of(totalSize % chunkSize).filter(i -> i > 0));
  }

  public TypeHints getHints() {
    return hints;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CollectionViewType that = (CollectionViewType) o;
    return maxLength == that.maxLength && elementType.equals(that.elementType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxLength, elementType);
  }
}
