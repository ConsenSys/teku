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

package tech.pegasys.teku.storage.server;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.api.Eth1DepositChannel;
import tech.pegasys.teku.util.async.SafeFuture;

public class DepositStorage implements Eth1DepositChannel, Eth1EventsChannel {
  private final EventBus eventBus;
  private final Database database;
  private final Eth1EventsChannel eth1EventsChannel;
  private volatile boolean isSyncingFromDatabase = false;
  private volatile Supplier<SafeFuture<ReplayDepositsResult>> replayResult;

  private DepositStorage(
      final EventBus eventBus, final Eth1EventsChannel eth1EventsChannel, final Database database) {
    this.eventBus = eventBus;
    this.eth1EventsChannel = eth1EventsChannel;
    this.database = database;
    this.replayResult = Suppliers.memoize(() -> SafeFuture.of(this::replayDeposits));
  }

  public static DepositStorage create(
      final EventBus eventBus, final Eth1EventsChannel eth1EventsChannel, final Database database) {
    return new DepositStorage(eventBus, eth1EventsChannel, database);
  }

  public void start() {
    eventBus.register(this);
  }

  public void stop() {
    eventBus.unregister(this);
  }

  @Override
  public SafeFuture<ReplayDepositsResult> replayDepositEvents() {
    return replayResult.get();
  }

  private ReplayDepositsResult replayDeposits() {
    isSyncingFromDatabase = true;
    final AtomicReference<UnsignedLong> lastDeposit = new AtomicReference<>();
    final AtomicReference<Boolean> isGenesisDone = new AtomicReference<>();
    lastDeposit.set(UnsignedLong.MAX_VALUE);
    isGenesisDone.set(false);
    final Optional<MinGenesisTimeBlockEvent> genesis = database.getMinGenesisTimeBlock();
    try (Stream<DepositsFromBlockEvent> eventStream = database.streamDepositsFromBlocks()) {
      eventStream.forEach(
          depositEvent -> {
            if (genesis.isPresent()
                && !isGenesisDone.get()
                && genesis.get().getBlockNumber().compareTo(depositEvent.getBlockNumber()) < 0) {
              this.eth1EventsChannel.onMinGenesisTimeBlock(genesis.get());
              isGenesisDone.set(true);
            }
            this.eth1EventsChannel.onDepositsFromBlock(depositEvent);

            lastDeposit.set(depositEvent.getBlockNumber());
          });
    }

    if (!isGenesisDone.get() && genesis.isPresent()) {
      this.eth1EventsChannel.onMinGenesisTimeBlock(genesis.get());
    }
    isSyncingFromDatabase = false;
    return new ReplayDepositsResult(lastDeposit.get(), genesis.isPresent());
  }

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    if (!isSyncingFromDatabase) {
      database.addDepositsFromBlockEvent(event);
    }
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    if (!isSyncingFromDatabase) {
      database.addMinGenesisTimeBlock(event);
    }
  }
}
