package tech.pegasys.artemis.util.backing;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;

public interface TreeNode {

  interface Root extends TreeNode {

    Bytes32 getRoot();

    @Override
    default Bytes32 hashTreeRoot() {
      return getRoot();
    }

    @Override
    default TreeNode get(long target) {
      checkArgument(target == 1, "Invalid root index: %s", target);
      return this;
    }

    @Override
    default TreeNode update(long target, Function<TreeNode, TreeNode> nodeUpdater) {
      checkArgument(target == 1, "Invalid root index: %s", target);
      return nodeUpdater.apply(this);
    }
  }

  interface Commit extends TreeNode {

    TreeNode left();

    TreeNode right();

    Commit rebind(boolean left, TreeNode newNode);

    @Override
    default Bytes32 hashTreeRoot() {
      return Hash.sha2_256(Bytes.concatenate(left().hashTreeRoot(), right().hashTreeRoot()));
    }

    @Override
    default TreeNode get(long target) {
      checkArgument(target >= 1, "Invalid index: %s", target);
      if (target == 1) {
        return this;
      } else {
        long anchor = Long.highestOneBit(target);
        long pivot = anchor >> 1;
        return target < (target | pivot)
            ? left().get(target ^ anchor | pivot)
            : right().get(target ^ anchor | pivot);
      }
    }

    @Override
    default TreeNode update(long target, Function<TreeNode, TreeNode> nodeUpdater) {
      if (target == 1) {
        return nodeUpdater.apply(this);
      } else {
        long anchor = Long.highestOneBit(target);
        long pivot = anchor >> 1;
        if (target < (target | pivot)) {
          TreeNode newLeftChild = left().update(target ^ anchor | pivot, nodeUpdater);
          return rebind(true, newLeftChild);
        } else {
          TreeNode newRightChild = right().update(target ^ anchor | pivot, nodeUpdater);
          return rebind(false, newRightChild);
        }
      }
    }
  }

  Bytes32 hashTreeRoot();

  TreeNode get(long generalizedIndex);

  TreeNode update(long generalizedIndex, Function<TreeNode, TreeNode> nodeUpdater);

  default TreeNode set(long target, TreeNode node) {
    return update(target, oldNode -> node);
  }
}
