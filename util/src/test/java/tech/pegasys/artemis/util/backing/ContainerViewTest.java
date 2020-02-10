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

package tech.pegasys.artemis.util.backing;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeUtil;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.PackedUInt64View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ContainerViewImpl;

public class ContainerViewTest {

  public interface ImmutableSubContainer extends ContainerViewRead<ViewRead> {

    UnsignedLong getLong1();

    Bytes32 getBytes1();
  }

  public interface SubContainerRead extends ContainerViewRead<ViewRead> {

    UnsignedLong getLong1();

    UnsignedLong getLong2();
  }

  public interface SubContainerWrite extends SubContainerRead, ContainerViewWrite<ViewRead> {

    void setLong1(UnsignedLong val);

    void setLong2(UnsignedLong val);
  }

  public interface ContainerRead extends ContainerViewRead<ViewRead> {

    UnsignedLong getLong1();

    UnsignedLong getLong2();

    SubContainerRead getSub1();

    ListViewRead<PackedUInt64View> getList1();

    ListViewRead<SubContainerRead> getList2();

    VectorViewRead<ImmutableSubContainer> getList3();

    @Override
    ContainerWrite createWritableCopy();
  }

  public interface ContainerWrite
      extends ContainerRead, ContainerViewWriteRef<ViewRead, ViewWrite> {

    void setLong1(UnsignedLong val);

    void setLong2(UnsignedLong val);

    @Override
    SubContainerWrite getSub1();

    @Override
    ListViewWrite<PackedUInt64View> getList1();

    @Override
    ListViewWriteRef<SubContainerRead, SubContainerWrite> getList2();

    @Override
    VectorViewWrite<ImmutableSubContainer> getList3();

    @Override
    ContainerRead commitChanges();
  }

  public static class ImmutableSubContainerImpl extends ContainerViewImpl<ImmutableSubContainerImpl>
      implements ImmutableSubContainer {

    public static final ContainerViewType<ImmutableSubContainerImpl> TYPE =
        new ContainerViewType<>(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE),
            ImmutableSubContainerImpl::new);

    private ImmutableSubContainerImpl(
        ContainerViewType<ImmutableSubContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public ImmutableSubContainerImpl(UnsignedLong long1, Bytes32 bytes1) {
      super(TYPE, new UInt64View(long1), new Bytes32View(bytes1));
    }

    @Override
    public UnsignedLong getLong1() {
      return ((UInt64View) get(0)).get();
    }

    @Override
    public Bytes32 getBytes1() {
      return ((Bytes32View) get(1)).get();
    }
  }

  public static class SubContainerImpl extends ContainerViewImpl<SubContainerImpl>
      implements SubContainerWrite {

    public static final ContainerViewType<SubContainerImpl> TYPE =
        new ContainerViewType<>(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.UINT64_TYPE), SubContainerImpl::new);

    private SubContainerImpl(ContainerViewType<SubContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    @Override
    public UnsignedLong getLong1() {
      return ((UInt64View) get(0)).get();
    }

    @Override
    public UnsignedLong getLong2() {
      return ((UInt64View) get(1)).get();
    }

    @Override
    public void setLong1(UnsignedLong val) {
      set(0, new UInt64View(val));
    }

    @Override
    public void setLong2(UnsignedLong val) {
      set(1, new UInt64View(val));
    }
  }

  public static class ContainerImpl extends ContainerViewImpl<ContainerImpl>
      implements ContainerWrite {

    public static final ContainerViewType<ContainerImpl> TYPE =
        new ContainerViewType<>(
            List.of(
                BasicViewTypes.UINT64_TYPE,
                BasicViewTypes.UINT64_TYPE,
                SubContainerImpl.TYPE,
                new ListViewType<>(BasicViewTypes.PACKED_UINT64_TYPE, 10),
                new ListViewType<>(SubContainerImpl.TYPE, 2),
                new VectorViewType<>(ImmutableSubContainerImpl.TYPE, 2)),
            ContainerImpl::new);

    public ContainerImpl(ContainerViewType<ContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public static ContainerRead createDefault() {
      return TYPE.createDefault();
    }

    @Override
    public UnsignedLong getLong1() {
      return ((UInt64View) get(0)).get();
    }

    @Override
    public UnsignedLong getLong2() {
      return ((UInt64View) get(1)).get();
    }

    @Override
    public SubContainerImpl getSub1() {
      return (SubContainerImpl) getByRef(2);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListViewWrite<PackedUInt64View> getList1() {
      return (ListViewWrite<PackedUInt64View>) getByRef(3);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListViewWriteRef<SubContainerRead, SubContainerWrite> getList2() {
      return (ListViewWriteRef<SubContainerRead, SubContainerWrite>) getByRef(4);
    }

    @Override
    @SuppressWarnings("unchecked")
    public VectorViewWrite<ImmutableSubContainer> getList3() {
      return (VectorViewWrite<ImmutableSubContainer>) getByRef(5);
    }

    @Override
    public void setLong1(UnsignedLong val) {
      set(0, new UInt64View(val));
    }

    @Override
    public void setLong2(UnsignedLong val) {
      set(1, new UInt64View(val));
    }
  }

  @Test
  public void readWriteContainerTest1() {
    ContainerRead c1 = ContainerImpl.createDefault();

    {
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getSub1().getLong1());
      Assertions.assertTrue(c1.getList1().isEmpty());
      Assertions.assertTrue(c1.getList2().isEmpty());
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getList3().get(0).getLong1());
      Assertions.assertEquals(Bytes32.ZERO, c1.getList3().get(0).getBytes1());
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getList3().get(1).getLong1());
      Assertions.assertEquals(Bytes32.ZERO, c1.getList3().get(1).getBytes1());
      Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> {
            c1.getList3().get(2);
          });
    }

    ContainerWrite c1w = c1.createWritableCopy();
    c1w.setLong1(UnsignedLong.valueOf(0x1));
    c1w.setLong2(UnsignedLong.valueOf(0x2));

    c1w.getSub1().setLong1(UnsignedLong.valueOf(0x111));
    c1w.getSub1().setLong2(UnsignedLong.valueOf(0x222));

    c1w.getList1().append(PackedUInt64View.fromLong(0x333));
    c1w.getList1().append(PackedUInt64View.fromLong(0x444));

    c1w.getList2()
        .append(
            sc -> {
              sc.setLong1(UnsignedLong.valueOf(0x555));
              sc.setLong2(UnsignedLong.valueOf(0x666));
            });
    SubContainerWrite sc1w = c1w.getList2().append();
    sc1w.setLong1(UnsignedLong.valueOf(0x777));
    sc1w.setLong2(UnsignedLong.valueOf(0x888));

    c1w.getList3()
        .set(
            1,
            new ImmutableSubContainerImpl(
                UnsignedLong.valueOf(0x999), Bytes32.leftPad(Bytes.fromHexString("0xa999"))));

    {
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getSub1().getLong1());
      Assertions.assertTrue(c1.getList1().isEmpty());
      Assertions.assertTrue(c1.getList2().isEmpty());
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getList3().get(0).getLong1());
      Assertions.assertEquals(Bytes32.ZERO, c1.getList3().get(0).getBytes1());
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getList3().get(1).getLong1());
      Assertions.assertEquals(Bytes32.ZERO, c1.getList3().get(1).getBytes1());

      Assertions.assertEquals(UnsignedLong.valueOf(0x1), c1w.getLong1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x2), c1w.getLong2());
      Assertions.assertEquals(UnsignedLong.valueOf(0x111), c1w.getSub1().getLong1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x222), c1w.getSub1().getLong2());
      Assertions.assertEquals(2, c1w.getList1().size());
      Assertions.assertEquals(UnsignedLong.valueOf(0x333), c1w.getList1().get(0).get());
      Assertions.assertEquals(UnsignedLong.valueOf(0x444), c1w.getList1().get(1).get());
      Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> {
            c1w.getList1().get(2);
          });
      Assertions.assertEquals(2, c1w.getList2().size());
      Assertions.assertEquals(UnsignedLong.valueOf(0x555), c1w.getList2().get(0).getLong1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x666), c1w.getList2().get(0).getLong2());
      Assertions.assertEquals(UnsignedLong.valueOf(0x777), c1w.getList2().get(1).getLong1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x888), c1w.getList2().get(1).getLong2());
      Assertions.assertEquals(UnsignedLong.ZERO, c1w.getList3().get(0).getLong1());
      Assertions.assertEquals(Bytes32.ZERO, c1w.getList3().get(0).getBytes1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x999), c1w.getList3().get(1).getLong1());
      Assertions.assertEquals(
          Bytes32.leftPad(Bytes.fromHexString("0xa999")), c1w.getList3().get(1).getBytes1());
    }

    ContainerRead c1r = c1w.commitChanges();
    TreeUtil.dumpBinaryTree(c1r.getBackingNode());

    {
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getSub1().getLong1());
      Assertions.assertTrue(c1.getList1().isEmpty());
      Assertions.assertTrue(c1.getList2().isEmpty());
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getList3().get(0).getLong1());
      Assertions.assertEquals(Bytes32.ZERO, c1.getList3().get(0).getBytes1());
      Assertions.assertEquals(UnsignedLong.ZERO, c1.getList3().get(1).getLong1());
      Assertions.assertEquals(Bytes32.ZERO, c1.getList3().get(1).getBytes1());

      Assertions.assertEquals(UnsignedLong.valueOf(0x1), c1r.getLong1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x2), c1r.getLong2());
      Assertions.assertEquals(UnsignedLong.valueOf(0x111), c1r.getSub1().getLong1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x222), c1r.getSub1().getLong2());
      Assertions.assertEquals(2, c1r.getList1().size());
      Assertions.assertEquals(UnsignedLong.valueOf(0x333), c1r.getList1().get(0).get());
      Assertions.assertEquals(UnsignedLong.valueOf(0x444), c1r.getList1().get(1).get());
      Assertions.assertThrows(
          IllegalArgumentException.class,
          () -> {
            c1r.getList1().get(2);
          });
      Assertions.assertEquals(2, c1r.getList2().size());
      Assertions.assertEquals(UnsignedLong.valueOf(0x555), c1r.getList2().get(0).getLong1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x666), c1r.getList2().get(0).getLong2());
      Assertions.assertEquals(UnsignedLong.valueOf(0x777), c1r.getList2().get(1).getLong1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x888), c1r.getList2().get(1).getLong2());
      Assertions.assertEquals(UnsignedLong.ZERO, c1r.getList3().get(0).getLong1());
      Assertions.assertEquals(Bytes32.ZERO, c1r.getList3().get(0).getBytes1());
      Assertions.assertEquals(UnsignedLong.valueOf(0x999), c1r.getList3().get(1).getLong1());
      Assertions.assertEquals(
          Bytes32.leftPad(Bytes.fromHexString("0xa999")), c1r.getList3().get(1).getBytes1());
    }

    ContainerWrite c2w = c1r.createWritableCopy();
    c2w.getList2().getByRef(1).setLong2(UnsignedLong.valueOf(0xaaa));
    ContainerRead c2r = c2w.commitChanges();

    Assertions.assertEquals(UnsignedLong.valueOf(0x888), c1r.getList2().get(1).getLong2());
    Assertions.assertEquals(UnsignedLong.valueOf(0xaaa), c2r.getList2().get(1).getLong2());
  }

  @Test
  public void readWriteContainerTest2() {
    ContainerRead c1 = ContainerImpl.TYPE.createDefault();
    ContainerWrite c1w = c1.createWritableCopy();
    c1w.setLong1(UnsignedLong.valueOf(0x111));
    SubContainerWrite sc1w = c1w.getList2().append();
    sc1w.setLong1(UnsignedLong.valueOf(0x222));
    sc1w.setLong2(UnsignedLong.valueOf(0x333));
    ContainerRead c1r = c1w.commitChanges();
    TreeUtil.dumpBinaryTree(c1r.getBackingNode());
  }

  @Test
  public void simpleContainerTest() {
    ContainerImpl c1 = ContainerImpl.TYPE.createDefault();
    c1.setLong1(UnsignedLong.valueOf(0x111));
    c1.setLong2(UnsignedLong.valueOf(0x222));
    ListViewWrite<PackedUInt64View> list1 = c1.getList1();
    list1.append(PackedUInt64View.fromLong(0x333));
    //    c1.setList1(list1);
    TreeUtil.dumpBinaryTree(c1.getBackingNode());
  }
}
