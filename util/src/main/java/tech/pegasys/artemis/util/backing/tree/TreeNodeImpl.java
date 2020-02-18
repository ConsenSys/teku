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

package tech.pegasys.artemis.util.backing.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.Utils;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Root;

public class TreeNodeImpl {

  public static final Root ZERO_LEAF = new RootImpl(Bytes32.ZERO);

  public static class RootImpl implements Root {
    private final Bytes32 root;

    public RootImpl(Bytes32 root) {
      this.root = root;
    }

    @Override
    public Bytes32 getRoot() {
      return root;
    }

    @Override
    public String toString() {
      Bytes trimmed = root.trimLeadingZeros();
      if (trimmed.size() > 4) {
        trimmed = root;
      }
      return "[" + trimmed + "]";
    }
  }

  public static class CommitImpl implements Commit {
    private final TreeNode left;
    private final TreeNode right;
    private volatile Bytes32 cachedHash = null;

    public CommitImpl(TreeNode left, TreeNode right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public TreeNode left() {
      return left;
    }

    @Override
    public TreeNode right() {
      return right;
    }

    @Override
    public Commit rebind(boolean left, TreeNode newNode) {
      return left ? new CommitImpl(newNode, right()) : new CommitImpl(left(), newNode);
    }

    @Override
    public Bytes32 hashTreeRoot() {
      if (cachedHash == null) {
        cachedHash = Commit.super.hashTreeRoot();
      }
      return cachedHash;
    }

    @Override
    public String toString() {
      return "(" +
          (left == right ? "default" : left + ", " + right)
          + ')';
    }
  }

  public static TreeNode createDefaultTree(int maxLength, TreeNode zeroElement) {
    List<TreeNode> nodes = Stream.concat(
        IntStream.range(0, maxLength)
            .mapToObj(i -> zeroElement),
        IntStream.range(maxLength, (int) Utils.nextPowerOf2(maxLength))
            .mapToObj(i -> TreeNodeImpl.ZERO_LEAF)
    ).collect(Collectors.toList());
    while (nodes.size() > 1) {
      List<TreeNode> parentNodes = new ArrayList<>(nodes.size() / 2);
      for (int i = 0; i < nodes.size(); i+=2) {
        parentNodes.add(new CommitImpl(nodes.get(i), nodes.get(i + 1)));
      }
      nodes = parentNodes;
    }
    return nodes.get(0);
  }

  public static TreeNode createZeroTree(long maxLength) {
    int depth = treeDepth(maxLength);
    TreeNode ret = TreeNodeImpl.ZERO_LEAF;
    for (int i = 0; i < depth; i++) {
      ret = new CommitImpl(ret, ret);
    }
    return ret;
  }

  public static TreeNode createTree(List<TreeNode> leafNodes) {
    int treeWidth = (int) Utils.nextPowerOf2(leafNodes.size());
    List<TreeNode> nodes = new ArrayList<>(leafNodes);
    nodes.addAll(Collections.nCopies(treeWidth - leafNodes.size(), ZERO_LEAF));
    while (nodes.size() > 1) {
      List<TreeNode> upperLevelNodes = new ArrayList<>(nodes.size() / 2);
      for (int i = 0; i < nodes.size() / 2; i++) {
        upperLevelNodes.add(new CommitImpl(nodes.get(i * 2), nodes.get(i * 2 + 1)));
      }
      nodes = upperLevelNodes;
    }
    return nodes.get(0);
  }

  private static int treeDepth(long maxChunks) {
    return Long.bitCount(Utils.nextPowerOf2(maxChunks) - 1);
  }
}
