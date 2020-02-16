/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.util.async.SafeFuture;

class HistoricalChainDataTest {
  private static final Optional<SignedBeaconBlock> BLOCK =
      Optional.of(DataStructureUtil.randomSignedBeaconBlock(1, 100));
  private final EventBus eventBus = mock(EventBus.class);
  private final HistoricalChainData historicalChainData = new HistoricalChainData(eventBus);

  @Test
  public void shouldRegisterWithEventBus() {
    verify(eventBus).register(historicalChainData);
  }

  @Test
  public void getFinalizedBlockAtSlot_shouldRetrieveBlockBySlot() {
    final SafeFuture<Optional<SignedBeaconBlock>> result =
        historicalChainData.getFinalizedBlockAtSlot(ONE);
    verify(eventBus).post(new GetFinalizedBlockAtSlotRequest(ONE));
    assertThat(result).isNotDone();

    historicalChainData.onBlockAtSlotResponse(new GetFinalizedBlockAtSlotResponse(ONE, BLOCK));
    assertThat(result).isCompletedWithValue(BLOCK);
  }

  @Test
  public void getFinalizedBlockAtSlot_shouldResolveWithEmptyOptionalWhenBlockNotAvailable() {
    final SafeFuture<Optional<SignedBeaconBlock>> result =
        historicalChainData.getFinalizedBlockAtSlot(ONE);

    historicalChainData.onBlockAtSlotResponse(
        new GetFinalizedBlockAtSlotResponse(ONE, Optional.empty()));
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getFinalizedBlockAtSlot_shouldIgnoreBlocksThatDoNotMatchOutstandingRequests() {
    historicalChainData.onBlockAtSlotResponse(new GetFinalizedBlockAtSlotResponse(ONE, BLOCK));
  }

  @Test
  public void
      getFinalizedBlockAtSlot_shouldResolveMultipleRequestsForTheSameSlotWithFirstAvailableData() {
    final SafeFuture<Optional<SignedBeaconBlock>> result1 =
        historicalChainData.getFinalizedBlockAtSlot(ONE);
    final SafeFuture<Optional<SignedBeaconBlock>> result2 =
        historicalChainData.getFinalizedBlockAtSlot(ONE);

    assertThat(result1).isNotDone();
    assertThat(result2).isNotDone();

    historicalChainData.onBlockAtSlotResponse(new GetFinalizedBlockAtSlotResponse(ONE, BLOCK));

    assertThat(result1).isCompletedWithValue(BLOCK);
    assertThat(result2).isCompletedWithValue(BLOCK);
  }

  @Test
  public void getLatestFinalizedBlockAtSlot_shouldRetrieveBlockBySlot() {
    final SafeFuture<Optional<SignedBeaconBlock>> result =
        historicalChainData.getLatestFinalizedBlockAtSlot(ONE);
    verify(eventBus).post(new GetLatestFinalizedBlockAtSlotRequest(ONE));
    assertThat(result).isNotDone();

    historicalChainData.onLatestBlockAtSlotResponse(
        new GetLatestFinalizedBlockAtSlotResponse(ONE, BLOCK));
    assertThat(result).isCompletedWithValue(BLOCK);
  }

  @Test
  public void getLatestFinalizedBlockAtSlot_shouldResolveWithEmptyOptionalWhenBlockNotAvailable() {
    final SafeFuture<Optional<SignedBeaconBlock>> result =
        historicalChainData.getLatestFinalizedBlockAtSlot(ONE);

    historicalChainData.onLatestBlockAtSlotResponse(
        new GetLatestFinalizedBlockAtSlotResponse(ONE, Optional.empty()));
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  public void getLatestFinalizedBlockAtSlot_shouldIgnoreBlocksThatDoNotMatchOutstandingRequests() {
    historicalChainData.onLatestBlockAtSlotResponse(
        new GetLatestFinalizedBlockAtSlotResponse(ONE, BLOCK));
  }

  @Test
  public void
      getLatestFinalizedBlockAtSlot_shouldResolveMultipleRequestsForTheSameSlotWithFirstAvailableData() {
    final SafeFuture<Optional<SignedBeaconBlock>> result1 =
        historicalChainData.getLatestFinalizedBlockAtSlot(ONE);
    final SafeFuture<Optional<SignedBeaconBlock>> result2 =
        historicalChainData.getLatestFinalizedBlockAtSlot(ONE);

    assertThat(result1).isNotDone();
    assertThat(result2).isNotDone();

    historicalChainData.onLatestBlockAtSlotResponse(
        new GetLatestFinalizedBlockAtSlotResponse(ONE, BLOCK));

    assertThat(result1).isCompletedWithValue(BLOCK);
    assertThat(result2).isCompletedWithValue(BLOCK);
  }

  @Test
  public void shouldResolveMultipleRequestsOfDifferentTypes() {
    final SafeFuture<Optional<SignedBeaconBlock>> result1 =
        historicalChainData.getFinalizedBlockAtSlot(ONE);
    final SafeFuture<Optional<SignedBeaconBlock>> result2 =
        historicalChainData.getLatestFinalizedBlockAtSlot(ONE);

    assertThat(result1).isNotDone();
    assertThat(result2).isNotDone();

    historicalChainData.onLatestBlockAtSlotResponse(
        new GetLatestFinalizedBlockAtSlotResponse(ONE, BLOCK));

    assertThat(result1).isNotCompleted();
    assertThat(result2).isCompletedWithValue(BLOCK);

    historicalChainData.onBlockAtSlotResponse(new GetFinalizedBlockAtSlotResponse(ONE, BLOCK));

    assertThat(result1).isCompletedWithValue(BLOCK);
  }
}
