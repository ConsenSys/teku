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

package tech.pegasys.teku.sync.multipeer.batches;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.sync.multipeer.batches.BatchAssert.assertThatBatch;
import static tech.pegasys.teku.sync.multipeer.chains.TargetChainTestUtil.chainWith;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.sync.multipeer.chains.TargetChain;

abstract class AbstractBatchTest {
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  protected final TargetChain targetChain =
      chainWith(new SlotAndBlockRoot(UInt64.valueOf(1000), Bytes32.ZERO));

  @Test
  void getFirstSlot_shouldReturnFirstSlot() {
    final long firstSlot = 5;
    final Batch batch = createBatch(firstSlot, 20);
    assertThatBatch(batch).hasFirstSlot(firstSlot);
  }

  @Test
  void getLastSlot_shouldCalculateLastSlot() {
    final Batch batch = createBatch(5, 20);
    assertThatBatch(batch).hasLastSlot(UInt64.valueOf(25));
  }

  @Test
  void isComplete_shouldNotBeCompleteOnCreation() {
    assertThatBatch(createBatch(10, 15)).isNotComplete();
  }

  @Test
  void isComplete_shouldBeCompleteAfterMarkComplete() {
    final Batch batch = createBatch(10, 15);
    batch.markComplete();
    assertThatBatch(batch).isComplete();
  }

  @Test
  void isComplete_shouldBeCompleteAfterInitialRequestReturnsNoBlocks() {
    final Batch batch = createBatch(10, 3);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    assertThatBatch(batch).isComplete();
    assertThatBatch(batch).isEmpty();
  }

  @Test
  void isComplete_shouldBeCompleteAfterInitialRequestReturnsBlockInLastSlot() {
    final Batch batch = createBatch(10, 3);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(13));
    assertThatBatch(batch).isComplete();
    assertThatBatch(batch).isNotEmpty();
  }

  @Test
  void getFirstBlock_shouldBeEmptyInitially() {
    assertThat(createBatch(10, 1).getFirstBlock()).isEmpty();
  }

  @Test
  void getFirstBlock_shouldBeEmptyAfterEmptyResponse() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    assertThat(batch.getFirstBlock()).isEmpty();
  }

  @Test
  void getFirstBlock_shouldContainFirstReturnedBlock() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    receiveBlocks(batch, firstBlock, dataStructureUtil.randomSignedBeaconBlock(11));
    assertThat(batch.getFirstBlock()).contains(firstBlock);
  }

  @Test
  void getFirstBlock_shouldBeFirstBlockAfterMultipleRequests() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    receiveBlocks(batch, firstBlock, dataStructureUtil.randomSignedBeaconBlock(11));

    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, dataStructureUtil.randomSignedBeaconBlock(12));
    assertThat(batch.getFirstBlock()).contains(firstBlock);
  }

  @Test
  void getLastBlock_shouldBeEmptyInitially() {
    assertThat(createBatch(10, 1).getLastBlock()).isEmpty();
  }

  @Test
  void getLastBlock_shouldBeEmptyAfterEmptyResponse() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch);
    assertThat(batch.getLastBlock()).isEmpty();
  }

  @Test
  void getLastBlock_shouldContainLastReturnedBlock() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final SignedBeaconBlock lastBlock = dataStructureUtil.randomSignedBeaconBlock(11);
    receiveBlocks(batch, firstBlock, lastBlock);
    assertThat(batch.getLastBlock()).contains(lastBlock);
  }

  @Test
  void getLastBlock_shouldBeLastBlockAfterMultipleRequests() {
    final Batch batch = createBatch(10, 7);
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(
        batch,
        dataStructureUtil.randomSignedBeaconBlock(10),
        dataStructureUtil.randomSignedBeaconBlock(11));

    batch.requestMoreBlocks(() -> {});
    final SignedBeaconBlock lastBlock = dataStructureUtil.randomSignedBeaconBlock(12);
    receiveBlocks(batch, lastBlock);
    assertThat(batch.getLastBlock()).contains(lastBlock);
  }

  @Test
  void getBlocks_shouldEmptyListInitially() {
    assertThat(createBatch(5, 6).getBlocks()).isEmpty();
  }

  @Test
  void getBlocks_shouldReturnAllBlocksFromMultipleRequests() {
    final Batch batch = createBatch(0, 60);
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2, block1.getRoot());
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(3, block2.getRoot());
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(4, block3.getRoot());
    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, block1, block2);
    assertThat(batch.getBlocks()).containsExactly(block1, block2);

    batch.requestMoreBlocks(() -> {});
    receiveBlocks(batch, block3, block4);
    assertThat(batch.getBlocks()).containsExactly(block1, block2, block3, block4);
  }

  @Test
  void isConfirmed_shouldOnlyBeConfirmedOnceFirstAndLastBlocksAreConfirmed() {
    final Batch batch = createBatch(75, 22);
    assertThatBatch(batch).isNotConfirmed();

    batch.markFirstBlockConfirmed();
    assertThatBatch(batch).isNotConfirmed();

    batch.markLastBlockConfirmed();
    assertThatBatch(batch).isConfirmed();
  }

  @Test
  void isConfirmed_shouldNotBeConfirmedWhenOnlyLastBlockIsConfirmed() {
    final Batch batch = createBatch(75, 22);
    assertThatBatch(batch).isNotConfirmed();

    batch.markLastBlockConfirmed();
    assertThatBatch(batch).isNotConfirmed();

    batch.markFirstBlockConfirmed();
    assertThatBatch(batch).isConfirmed();
  }

  @Test
  void isFirstBlockConfirmed_shouldBeTrueOnlyAfterBeingMarked() {
    final Batch batch = createBatch(1, 3);
    assertThatBatch(batch).hasUnconfirmedFirstBlock();

    batch.markFirstBlockConfirmed();
    assertThatBatch(batch).hasConfirmedFirstBlock();
  }

  @Test
  void isContested_shouldBeContestedWhenMarkedContested() {
    final Batch batch = createBatch(5, 10);
    assertThatBatch(batch).isNotContested();

    batch.markAsContested();
    assertThatBatch(batch).isContested();
  }

  @Test
  @Disabled
  void markInvalid_shouldDoSomethingGood() {}

  protected abstract Batch createBatch(final long startSlot, final long count);

  protected abstract void receiveBlocks(final Batch batch, final SignedBeaconBlock... blocks);
}
