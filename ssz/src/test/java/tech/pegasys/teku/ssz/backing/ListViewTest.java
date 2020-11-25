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

package tech.pegasys.teku.ssz.backing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.TestUtil.TestContainer;
import tech.pegasys.teku.ssz.TestUtil.TestSubContainer;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.TypeHints;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;

public class ListViewTest {

  @Test
  void clearTest() {
    ListViewType<TestSubContainer> type = new ListViewType<>(TestSubContainer.TYPE, 100);
    ListViewRead<TestSubContainer> lr1 = type.getDefault();
    ListViewWrite<TestSubContainer> lw1 = lr1.createWritableCopy();
    lw1.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    lw1.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewWrite<TestSubContainer> lw2 = lw1.commitChanges().createWritableCopy();
    lw2.clear();
    ListViewRead<TestSubContainer> lr2 = lw2.commitChanges();
    assertThat(lr1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    ListViewWrite<TestSubContainer> lw3 = lw1.commitChanges().createWritableCopy();
    lw3.clear();
    lw3.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewRead<TestSubContainer> lr3 = lw3.commitChanges();
    assertThat(lr3.size()).isEqualTo(1);
  }

  @Test
  void superLeafNodeListTest() {
    ListViewType<UInt64View> lt1 = new ListViewType<>(BasicViewTypes.UINT64_TYPE, 10);
    ListViewType<UInt64View> lt2 =
        new ListViewType<>(BasicViewTypes.UINT64_TYPE, 10, TypeHints.superLeaf());

    ListViewRead<UInt64View> l1_0 = lt1.getDefault();
    ListViewRead<UInt64View> l2_0 = lt2.getDefault();

    assertThat(l2_0.hashTreeRoot()).isEqualTo(l1_0.hashTreeRoot());

    ListViewWrite<UInt64View> l1w_0 = l1_0.createWritableCopy();
    l1w_0.append(UInt64View.fromLong(0x77889900));
    ListViewRead<UInt64View> l1_1 = l1w_0.commitChanges();

    ListViewWrite<UInt64View> l2w_0 = l2_0.createWritableCopy();
    l2w_0.append(UInt64View.fromLong(0x77889900));
    ListViewRead<UInt64View> l2_1 = l2w_0.commitChanges();

    assertThat(l2_1.hashTreeRoot()).isEqualTo(l1_1.hashTreeRoot());

    ListViewWrite<UInt64View> l1w_1 = l1_1.createWritableCopy();
    l1w_1.append(UInt64View.fromLong(0x111111));
    l1w_1.append(UInt64View.fromLong(0x222222));
    l1w_1.append(UInt64View.fromLong(0x333333));
    l1w_1.append(UInt64View.fromLong(0x444444));
    ListViewRead<UInt64View> l1_2 = l1w_1.commitChanges();

    ListViewWrite<UInt64View> l2w_1 = l2_1.createWritableCopy();
    l2w_1.append(UInt64View.fromLong(0x111111));
    l2w_1.append(UInt64View.fromLong(0x222222));
    l2w_1.append(UInt64View.fromLong(0x333333));
    l2w_1.append(UInt64View.fromLong(0x444444));
    ListViewRead<UInt64View> l2_2 = l2w_1.commitChanges();

    assertThat(l2_2.hashTreeRoot()).isEqualTo(l1_2.hashTreeRoot());

    ListViewWrite<UInt64View> l1w_2 = l1_2.createWritableCopy();
    l1w_2.set(0, UInt64View.fromLong(0x555555));
    l1w_2.set(4, UInt64View.fromLong(0x666666));
    ListViewRead<UInt64View> l1_3 = l1w_2.commitChanges();

    ListViewWrite<UInt64View> l2w_2 = l2_2.createWritableCopy();
    l2w_2.set(0, UInt64View.fromLong(0x555555));
    l2w_2.set(4, UInt64View.fromLong(0x666666));
    ListViewRead<UInt64View> l2_3 = l2w_2.commitChanges();

    assertThat(l2_3.hashTreeRoot()).isEqualTo(l1_3.hashTreeRoot());
    assertThat(l2_3.sszSerialize()).isEqualTo(l1_3.sszSerialize());
  }

  @Test
  void largeSuperLeafNodeListTest() {
    long maxLen = 1L << 38;
    ListViewType<UInt64View> lt1 = new ListViewType<>(BasicViewTypes.UINT64_TYPE, maxLen);
    ListViewType<UInt64View> lt2 =
        new ListViewType<>(BasicViewTypes.UINT64_TYPE, maxLen, TypeHints.superLeaf());

    ListViewRead<UInt64View> l1_0 = lt1.getDefault();
    ListViewRead<UInt64View> l2_0 = lt2.getDefault();

    assertThat(l2_0.hashTreeRoot()).isEqualTo(l1_0.hashTreeRoot());

    ListViewWrite<UInt64View> l1w_0 = l1_0.createWritableCopy();
    for (int i = 0; i < 16000; i++) {
      l1w_0.append(UInt64View.fromLong(0x77889900 + i));
    }
    ListViewRead<UInt64View> l1_1 = l1w_0.commitChanges();

    ListViewWrite<UInt64View> l2w_0 = l2_0.createWritableCopy();
    for (int i = 0; i < 16000; i++) {
      l2w_0.append(UInt64View.fromLong(0x77889900 + i));
    }
    ListViewRead<UInt64View> l2_1 = l2w_0.commitChanges();

    assertThat(l2_1.hashTreeRoot()).isEqualTo(l1_1.hashTreeRoot());
  }

  @Test
  void superBranchNodeTest() {
    ListViewType<Bytes32View> lt1 = new ListViewType<>(BasicViewTypes.BYTES32_TYPE, 10);
    ListViewType<Bytes32View> lt2 =
        new ListViewType<>(BasicViewTypes.BYTES32_TYPE, 10, TypeHints.superBranch(List.of(3, 1)));

    ListViewRead<Bytes32View> l1_0 = lt1.getDefault();
    ListViewRead<Bytes32View> l2_0 = lt2.getDefault();

    System.out.println(l1_0.getBackingNode());
    System.out.println(l2_0.getBackingNode());

    assertThat(l2_0.hashTreeRoot()).isEqualTo(l1_0.hashTreeRoot());

    ListViewWrite<Bytes32View> l1w_0 = l1_0.createWritableCopy();
    l1w_0.append(bytes32View(0x77889900));
    ListViewRead<Bytes32View> l1_1 = l1w_0.commitChanges();

    ListViewWrite<Bytes32View> l2w_0 = l2_0.createWritableCopy();
    l2w_0.append(bytes32View(0x77889900));
    ListViewRead<Bytes32View> l2_1 = l2w_0.commitChanges();

    System.out.println(l1_1.getBackingNode());
    System.out.println(l2_1.getBackingNode());

    assertThat(l2_1.hashTreeRoot()).isEqualTo(l1_1.hashTreeRoot());

    ListViewWrite<Bytes32View> l1w_1 = l1_1.createWritableCopy();
    l1w_1.append(bytes32View(0x111111));
    l1w_1.append(bytes32View(0x222222));
    l1w_1.append(bytes32View(0x333333));
    l1w_1.append(bytes32View(0x444444));
    ListViewRead<Bytes32View> l1_2 = l1w_1.commitChanges();

    ListViewWrite<Bytes32View> l2w_1 = l2_1.createWritableCopy();
    l2w_1.append(bytes32View(0x111111));
    l2w_1.append(bytes32View(0x222222));
    l2w_1.append(bytes32View(0x333333));
    l2w_1.append(bytes32View(0x444444));
    ListViewRead<Bytes32View> l2_2 = l2w_1.commitChanges();

    assertThat(l2_2.hashTreeRoot()).isEqualTo(l1_2.hashTreeRoot());

    ListViewWrite<Bytes32View> l1w_2 = l1_2.createWritableCopy();
    l1w_2.set(0, bytes32View(0x555555));
    l1w_2.set(4, bytes32View(0x666666));
    ListViewRead<Bytes32View> l1_3 = l1w_2.commitChanges();

    ListViewWrite<Bytes32View> l2w_2 = l2_2.createWritableCopy();
    l2w_2.set(0, bytes32View(0x555555));
    l2w_2.set(4, bytes32View(0x666666));
    ListViewRead<Bytes32View> l2_3 = l2w_2.commitChanges();

    assertThat(l2_3.hashTreeRoot()).isEqualTo(l1_3.hashTreeRoot());
    assertThat(l2_3.sszSerialize()).isEqualTo(l1_3.sszSerialize());
  }

  private static Bytes32View bytes32View(long l) {
    return new Bytes32View(Bytes32.leftPad(Bytes.ofUnsignedLong(l)));
  }

  @Test
  void sszSuperNodeTest() {
    ListViewType<Bytes32View> lt1 = new ListViewType<>(TestContainer.TYPE, 10,
        TypeHints.sszSuperLeaf(2));
  }
}
