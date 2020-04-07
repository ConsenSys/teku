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

package tech.pegasys.artemis.statetransition.protoarray;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class ProtoArray {

  private int pruneThreshold;
  private UnsignedLong justifiedEpoch;
  private UnsignedLong finalizedEpoch;

  private final List<ProtoNode> nodes;
  private final Map<Bytes32, Integer> indices;

  public ProtoArray(
      int pruneThreshold,
      UnsignedLong justifiedEpoch,
      UnsignedLong finalizedEpoch,
      List<ProtoNode> nodes,
      Map<Bytes32, Integer> indices) {
    this.pruneThreshold = pruneThreshold;
    this.justifiedEpoch = justifiedEpoch;
    this.finalizedEpoch = finalizedEpoch;
    this.nodes = nodes;
    this.indices = indices;
  }

  public Map<Bytes32, Integer> getIndices() {
    return indices;
  }

  public List<ProtoNode> getNodes() {
    return nodes;
  }

  public void setPruneThreshold(int pruneThreshold) {
    this.pruneThreshold = pruneThreshold;
  }

  // Register a block with the fork choice.
  //
  // It is only sane to supply a `None` parent for the genesis block.
  public void onBlock(
      UnsignedLong slot,
      Bytes32 root,
      Optional<Bytes32> optionalParentRoot,
      Bytes32 stateRoot,
      UnsignedLong justifiedEpoch,
      UnsignedLong finalizedEpoch) {
    if (indices.containsKey(root)) {
      return;
    }

    int nodeIndex = nodes.size();

    ProtoNode node =
        new ProtoNode(
            slot,
            stateRoot,
            root,
            optionalParentRoot.map(indices::get),
            justifiedEpoch,
            finalizedEpoch,
            UnsignedLong.ZERO,
            Optional.empty(),
            Optional.empty());

    indices.put(node.getRoot(), nodeIndex);
    nodes.add(node);

    node.getParentIndex()
        .ifPresent(parentIndex -> maybeUpdateBestChildAndDescendant(parentIndex, nodeIndex));
  }

  // Follows the best-descendant links to find the best-block (i.e., head-block).
  //
  // ## Notes
  //
  // The result of this function is not guaranteed to be accurate if `Self::on_new_block` has
  // been called without a subsequent `Self::apply_score_changes` call. This is because
  // `on_new_block` does not attempt to walk backwards through the tree and update the
  // best-child/best-descendant links.
  public Bytes32 findHead(Bytes32 justifiedRoot) {
    int justifiedIndex = indices.get(justifiedRoot);
    ProtoNode justifiedNode = nodes.get(justifiedIndex);

    int bestDescendantIndex = justifiedNode.getBestDescendantIndex().orElse(justifiedIndex);
    ProtoNode bestNode = nodes.get(bestDescendantIndex);

    // Perform a sanity check that the node is indeed valid to be the head.
    if (!nodeIsViableForHead(bestNode)) {
      throw new RuntimeException("ProtoArray: Best node is not viable for head");
    }

    return bestNode.getRoot();
  }

  // Iterate backwards through the array, touching all nodes and their parents and potentially
  // the best-child of each parent.
  //
  // The structure of the `this.nodes` array ensures that the child of each node is always
  // touched before its parent.
  //
  // For each node, the following is done:
  //
  // - Update the node's weight with the corresponding delta.
  // - Back-propagate each node's delta to its parents delta.
  // - Compare the current node with the parents best-child, updating it if the current node
  // should become the best child.
  // - If required, update the parents best-descendant with the current node or its best-descendant.
  public void applyScoreChanges(
      List<Long> deltas, UnsignedLong justifiedEpoch, UnsignedLong finalizedEpoch) {
    if (deltas.size() != indices.size()) {
      throw new RuntimeException("ProtoArray: Invalid delta length");
    }

    if (!justifiedEpoch.equals(this.justifiedEpoch)
        || !finalizedEpoch.equals(this.finalizedEpoch)) {
      this.justifiedEpoch = justifiedEpoch;
      this.finalizedEpoch = finalizedEpoch;
    }

    // Iterate backwards through all indices in `this.nodes`.
    for (int nodeIndex = nodes.size() - 1; nodeIndex >= 0; nodeIndex--) {
      ProtoNode node = nodes.get(nodeIndex);

      // There is no need to adjust the balances or manage parent of the zero hash since it
      // is an alias to the genesis block. The weight applied to the genesis block is
      // irrelevant as we _always_ choose it and it's impossible for it to have a parent.
      if (node.getRoot().equals(Bytes32.ZERO)) {
        continue;
      }

      long nodeDelta = deltas.get(nodeIndex);
      node.adjustWeight(nodeDelta);

      if (node.getParentIndex().isEmpty()) {
        continue;
      }

      int parentIndex = node.getParentIndex().get();
      deltas.set(parentIndex, deltas.get(parentIndex) + nodeDelta);
      maybeUpdateBestChildAndDescendant(parentIndex, nodeIndex);
    }
  }

  // Update the tree with new finalization information. The tree is only actually pruned if both
  // of the two following criteria are met:
  //
  // - The supplied finalized epoch and root are different to the current values.
  // - The number of nodes in `self` is at least `self.prune_threshold`.
  //
  // # Errors
  //
  // Throws errors if:
  //
  // - The finalized epoch is less than the current one.
  // - The finalized epoch is equal to the current one, but the finalized root is different.
  // - There is some internal error relating to invalid indices inside `self`.
  public void maybePrune(Bytes32 finalizedRoot) {
    int finalizedIndex = indices.get(finalizedRoot);

    if (finalizedIndex < pruneThreshold) {
      // Pruning at small numbers incurs more cost than benefit.
      return;
    }

    // Remove the `indices` key/values for all the to-be-deleted nodes.
    for (int nodeIndex = 0; nodeIndex < finalizedIndex; nodeIndex++) {
      Bytes32 root = nodes.get(nodeIndex).getRoot();
      indices.remove(root);
    }

    // Drop all the nodes prior to finalization.
    nodes.subList(0, finalizedIndex).clear();

    // Adjust the indices map.
    indices.replaceAll(
        (key, value) -> {
          int newIndex = value - finalizedIndex;
          if (newIndex < 0) {
            throw new RuntimeException("ProtoArray: New array index less than 0.");
          }
          return newIndex;
        });

    // Iterate through all the existing nodes and adjust their indices to match the
    // new layout of nodes.
    for (ProtoNode node : nodes) {
      node.getParentIndex()
          .ifPresent(
              parentIndex -> {
                // If node.parentIndex is less than finalizedIndex, set is to None.
                if (parentIndex < finalizedIndex) {
                  node.setParentIndex(Optional.empty());
                } else {
                  node.setParentIndex(Optional.of(parentIndex - finalizedIndex));
                }
              });

      node.getBestChildIndex()
          .ifPresent(
              bestChildIndex -> {
                int newBestChildIndex = bestChildIndex - finalizedIndex;
                if (newBestChildIndex < 0) {
                  throw new RuntimeException("ProtoArray: New best child index is less than 0");
                }
                node.setBestChildIndex(Optional.of(newBestChildIndex));
              });

      node.getBestDescendantIndex()
          .ifPresent(
              bestDescendantIndex -> {
                int newBestDescendantIndex = bestDescendantIndex - finalizedIndex;
                if (newBestDescendantIndex < 0) {
                  throw new RuntimeException(
                      "ProtoArray: New best descendant index is less than 0");
                }
                node.setBestDescendantIndex(Optional.of(newBestDescendantIndex));
              });
    }
  }

  // Observe the parent at `parentIndex` with respect to the child at `childIndex` and
  // potentially modify the `parent.bestChild` and `parent.bestDescendant` values.
  //
  // ## Detail
  //
  // There are four outcomes:
  //
  // - The child is already the best child but it's now invalid due to a FFG change and should be
  // removed.
  // - The child is already the best child and the parent is updated with the new best-descendant.
  // - The child is not the best child but becomes the best child.
  // - The child is not the best child and does not become the best child.
  private void maybeUpdateBestChildAndDescendant(int parentIndex, int childIndex) {
    ProtoNode child = nodes.get(childIndex);
    ProtoNode parent = nodes.get(parentIndex);

    boolean childLeadsToViableHead = nodeLeadsToViableHead(child);

    parent
        .getBestChildIndex()
        .ifPresentOrElse(
            bestChildIndex -> {
              if (bestChildIndex.equals(childIndex) && !childLeadsToViableHead) {
                // If the child is already the best-child of the parent but it's not viable for
                // the head, remove it.
                changeToNone(parent);
              } else if (bestChildIndex.equals(childIndex)) {
                // If the child is the best-child already, set it again to ensure that the
                // best-descendant of the parent is updated.
                changeToChild(parent, childIndex);
              } else {
                ProtoNode bestChild = nodes.get(bestChildIndex);

                boolean bestChildLeadsToViableHead = nodeLeadsToViableHead(bestChild);

                if (childLeadsToViableHead && !bestChildLeadsToViableHead) {
                  // The child leads to a viable head, but the current best-child doesn't.
                  changeToChild(parent, childIndex);
                } else if (!childLeadsToViableHead && bestChildLeadsToViableHead) {
                  // The best child leads to a viable head, but the child doesn't.
                  // No change.
                } else if (child.getWeight().equals(bestChild.getWeight())) {
                  // Tie-breaker of equal weights by root.
                  if (child.getRoot().compareTo(bestChild.getRoot()) >= 0) {
                    changeToChild(parent, childIndex);
                  } else {
                    // No change.
                  }
                } else {
                  // Choose the winner by weight.
                  if (child.getWeight().compareTo(bestChild.getWeight()) >= 0) {
                    changeToChild(parent, childIndex);
                  } else {
                    // No change.
                  }
                }
              }
            },
            () -> {
              if (childLeadsToViableHead) {
                // There is no current best-child and the child is viable.
                changeToChild(parent, childIndex);
              } else {
                // There is no current best-child but the child is not not viable.
                // No change.
              }
            });
  }

  // Helper for maybeUpdateBestChildAndDescendant
  private void changeToChild(ProtoNode parent, int childIndex) {
    ProtoNode child = nodes.get(childIndex);
    parent.setBestChildIndex(Optional.of(childIndex));
    parent.setBestDescendantIndex(Optional.of(child.getBestDescendantIndex().orElse(childIndex)));
  }

  // Helper for maybeUpdateBestChildAndDescendant
  private void changeToNone(ProtoNode parent) {
    parent.setBestChildIndex(Optional.empty());
    parent.setBestDescendantIndex(Optional.empty());
  }

  // Indicates if the node itself is viable for the head, or if it's best descendant is viable
  // for the head.
  private boolean nodeLeadsToViableHead(ProtoNode node) {
    boolean bestDescendantIsViableForHead =
        node.getBestDescendantIndex().map(nodes::get).map(this::nodeIsViableForHead).orElse(false);

    return bestDescendantIsViableForHead || nodeIsViableForHead(node);
  }

  // This is the equivalent to the `filter_block_tree` function in the eth2 spec:
  //
  // https://github.com/ethereum/eth2.0-specs/blob/v0.10.0/specs/phase0/fork-choice.md#filter_block_tree
  //
  // Any node that has a different finalized or justified epoch should not be viable for the
  // head.
  private boolean nodeIsViableForHead(ProtoNode node) {
    return (node.getJustifiedEpoch().equals(justifiedEpoch)
            || justifiedEpoch.equals(UnsignedLong.ZERO))
        && (node.getFinalizedEpoch().equals(finalizedEpoch)
            || finalizedEpoch.equals(UnsignedLong.ZERO));
  }
}
