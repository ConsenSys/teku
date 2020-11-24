package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.ssz.backing.tree.TreeNode.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode.LeafNode;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;

public class SszNodeTemplate {

  public static final class Location {
    public final int offset;
    public final int length;

    public Location(int offset, int length) {
      this.offset = offset;
      this.length = length;
    }
  }

  private static Location calcOffsets(int offset, TreeNode node, long thisGIdx, Map<Long, Location> locations) {
    if (node instanceof LeafNode) {
      Location location = new Location(offset, ((LeafNode) node).getData().size());
      locations.put(thisGIdx, location);
      return location;
    } else {
      BranchNode branchNode = (BranchNode) node;
      Location leftLoc = calcOffsets(offset, branchNode.left(),
          GIndexUtil.gIdxLeftGIndex(thisGIdx), locations);
      Location rightLoc = calcOffsets(offset + leftLoc.length, branchNode.right(),
          GIndexUtil.gIdxRightGIndex(thisGIdx), locations);
      Location thisLoc = new Location(offset, leftLoc.length + rightLoc.length);
      locations.put(thisGIdx, thisLoc);
      return thisLoc;
    }
  }

  public static SszNodeTemplate createFromContainerType(ContainerViewType<?> containerType) {
    checkArgument(containerType.isFixedSize(), "Only fixed size containers supported");

    // This should be CANONICAL binary tree
    TreeNode defaultTree = containerType.createDefaultCanonicalBinaryTree();

    Map<Long, Location> gIndexToLoc = new HashMap<>();
    Location rootLoc = calcOffsets(0, defaultTree, GIndexUtil.SELF_G_INDEX, gIndexToLoc);
    Map<TreeNode, Location> nodeToLoc = new IdentityHashMap<>();
    AtomicInteger off = new AtomicInteger();
    defaultTree.iterateAll((node, idx) -> {
      if (node instanceof LeafNode) {
        int leafSszSize = ((LeafNode) node).getData().size();
        Location nodeSszLocation = new Location(off.get(), leafSszSize);
        nodeToLoc.put(node, nodeSszLocation);
        off.addAndGet(leafSszSize);
      }
      return true;
    });
    assert rootLoc.length == off.get();
    return new SszNodeTemplate(gIndexToLoc, nodeToLoc, off.get(), defaultTree);
  }

  private final Map<Long, Location> gIndexToLoc;
  private final Map<TreeNode, Location> nodeToLoc;
  private final int sszLength;
  private final TreeNode defaultTree;

  public SszNodeTemplate(
      Map<Long, Location> gIndexToLoc,
      Map<TreeNode, Location> nodeToLoc, int sszLength,
      TreeNode defaultTree) {
    this.gIndexToLoc = gIndexToLoc;
    this.nodeToLoc = nodeToLoc;
    this.sszLength = sszLength;
    this.defaultTree = defaultTree;
  }

  public Location getNodeSszLocation(long generalizedIndex) {
    return gIndexToLoc.get(generalizedIndex);
  }

  public int getSszLength() {
    return sszLength;
  }

  public Bytes32 calculateHashTreeRoot(Bytes ssz, int offset) {
    return calcHash(ssz, offset, defaultTree);
  }

  private Bytes32 calcHash(Bytes ssz, int offset, TreeNode node) {
    if (node instanceof LeafNode) {
      Location location = nodeToLoc.get(node);
      return Bytes32.rightPad(ssz.slice(offset + location.offset, location.length));
    } else {
      BranchNode branchNode = (BranchNode) node;
      return Hash.sha2_256(Bytes.wrap(calcHash(ssz, offset, branchNode.left()),
          calcHash(ssz, offset, branchNode.right())));
    }
  }
}
