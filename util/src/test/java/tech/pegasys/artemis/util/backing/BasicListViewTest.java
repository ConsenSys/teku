package tech.pegasys.artemis.util.backing;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.PackedUnsignedLongView;

public class BasicListViewTest {

  @Test
  public void simpleUInt64ListTest() {
    ListViewType<PackedUnsignedLongView> listType = new ListViewType<>(
        BasicViewTypes.PACKED_UNSIGNED_LONG_TYPE, 7);
    ListView<PackedUnsignedLongView> listView = listType.createDefault();
    TreeNode n0 = listView.getBackingNode();
    listView.append(new PackedUnsignedLongView(UnsignedLong.valueOf(0x111)));
    TreeNode n1 = listView.getBackingNode();
    listView.append(new PackedUnsignedLongView(UnsignedLong.valueOf(0x222)));
    listView.append(new PackedUnsignedLongView(UnsignedLong.valueOf(0x333)));
    listView.append(new PackedUnsignedLongView(UnsignedLong.valueOf(0x444)));
    TreeNode n2 = listView.getBackingNode();
    listView.append(new PackedUnsignedLongView(UnsignedLong.valueOf(0x555)));
    TreeNode n3 = listView.getBackingNode();
    listView.append(new PackedUnsignedLongView(UnsignedLong.valueOf(0x666)));
    listView.append(new PackedUnsignedLongView(UnsignedLong.valueOf(0x777)));
    TreeNode n4 = listView.getBackingNode();
    listView.set(0, new PackedUnsignedLongView(UnsignedLong.valueOf(0x800)));
    TreeNode n5 = listView.getBackingNode();
    listView.set(1, new PackedUnsignedLongView(UnsignedLong.valueOf(0x801)));
    listView.set(2, new PackedUnsignedLongView(UnsignedLong.valueOf(0x802)));
    listView.set(3, new PackedUnsignedLongView(UnsignedLong.valueOf(0x803)));
    listView.set(4, new PackedUnsignedLongView(UnsignedLong.valueOf(0x804)));
    TreeNode n6 = listView.getBackingNode();
    System.out.println(n0);
    System.out.println(n1);
    System.out.println(n2);
    System.out.println(n3);
    System.out.println(n4);
    System.out.println(n5);
    System.out.println(n6);

    Assertions.assertEquals(0, listType.createFromTreeNode(n0).size());
    Assertions.assertEquals(1, listType.createFromTreeNode(n1).size());
    Assertions.assertEquals(0x111, listType.createFromTreeNode(n1).get(0).longValue());
    Assertions.assertEquals(4, listType.createFromTreeNode(n2).size());
    Assertions.assertEquals(0x111, listType.createFromTreeNode(n2).get(0).longValue());
    Assertions.assertEquals(0x222, listType.createFromTreeNode(n2).get(1).longValue());
    Assertions.assertEquals(0x333, listType.createFromTreeNode(n2).get(2).longValue());
    Assertions.assertEquals(0x444, listType.createFromTreeNode(n2).get(3).longValue());
    Assertions.assertEquals(5, listType.createFromTreeNode(n3).size());
    Assertions.assertEquals(0x111, listType.createFromTreeNode(n3).get(0).longValue());
    Assertions.assertEquals(0x222, listType.createFromTreeNode(n3).get(1).longValue());
    Assertions.assertEquals(0x333, listType.createFromTreeNode(n3).get(2).longValue());
    Assertions.assertEquals(0x444, listType.createFromTreeNode(n3).get(3).longValue());
    Assertions.assertEquals(0x555, listType.createFromTreeNode(n3).get(4).longValue());
    Assertions.assertEquals(7, listType.createFromTreeNode(n4).size());
    Assertions.assertEquals(0x666, listType.createFromTreeNode(n4).get(5).longValue());
    Assertions.assertEquals(0x777, listType.createFromTreeNode(n4).get(6).longValue());
    Assertions.assertEquals(0x800, listType.createFromTreeNode(n5).get(0).longValue());
    Assertions.assertEquals(0x222, listType.createFromTreeNode(n5).get(1).longValue());

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> listType.createFromTreeNode(n3)
            .set(7, new PackedUnsignedLongView(UnsignedLong.valueOf(0xaaa))));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> listType.createFromTreeNode(n3).get(7));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> listType.createFromTreeNode(n3).get(8));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> listView.set(7, new PackedUnsignedLongView(UnsignedLong.valueOf(0xaaa))));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> listView.append(new PackedUnsignedLongView(UnsignedLong.valueOf(0xaaa))));
  }
}
