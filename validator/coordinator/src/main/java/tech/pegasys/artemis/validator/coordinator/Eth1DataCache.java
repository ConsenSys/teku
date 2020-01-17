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

package tech.pegasys.artemis.validator.coordinator;

import static tech.pegasys.artemis.pow.Eth1DataManager.getCacheRangeLowerBound;
import static tech.pegasys.artemis.pow.Eth1DataManager.hasBeenApproximately;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.time.SlotEvent;

public class Eth1DataCache {

  private final EventBus eventBus;
  private Optional<UnsignedLong> genesisTime = Optional.empty();

  private final NavigableMap<UnsignedLong, Eth1Data> eth1ChainCache = new ConcurrentSkipListMap<>();
  private volatile UnsignedLong currentVotingPeriodStartTime;

  public Eth1DataCache(EventBus eventBus) {
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  public void startBeaconChainMode(BeaconState genesisState) {
    this.genesisTime = Optional.of(genesisState.getGenesis_time());
    this.currentVotingPeriodStartTime = getVotingPeriodStartTime(genesisState.getSlot());
  }

  @Subscribe
  public void onCacheEth1BlockEvent(CacheEth1BlockEvent cacheEth1BlockEvent) {
    eth1ChainCache.put(cacheEth1BlockEvent.getBlockTimestamp(), createEth1Data(cacheEth1BlockEvent));
  }

  @Subscribe
  public void onTick(Date date) {
    if (!hasBeenApproximately(SECONDS_PER_ETH1_BLOCK, date) || genesisTime.isPresent()) {
      return;
    }
    prune();
  }

  @Subscribe
  public void onSlot(SlotEvent slotEvent) {
    UnsignedLong slot = slotEvent.getSlot();
    UnsignedLong voting_period_start_time = getVotingPeriodStartTime(slot);

    if (voting_period_start_time.equals(currentVotingPeriodStartTime)) {
      return;
    }

    currentVotingPeriodStartTime = voting_period_start_time;
    prune(voting_period_start_time);
  }

  public Eth1Data get_eth1_vote(BeaconState state) {
    NavigableMap<UnsignedLong, Eth1Data> votesToConsider = getVotesToConsider();
    Map<Eth1Data, Eth1Vote> validVotes = new HashMap<>();

    int i = 0;
    for (Eth1Data eth1Data : state.getEth1_data_votes()) {
      if (!votesToConsider.containsValue(eth1Data)) {
        continue;
      }

      final int currentIndex = i;
      Eth1Vote vote =
          validVotes.computeIfAbsent(
              eth1Data,
              key -> {
                Eth1Vote newVote = new Eth1Vote();
                newVote.setIndex(currentIndex);
                return newVote;
              });
      vote.incrementVotes();
      i++;
    }

    Eth1Data defaultVote =
        !votesToConsider.isEmpty() ? votesToConsider.lastEntry().getValue() : state.getEth1_data();

    Optional<Eth1Data> vote =
        validVotes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);

    return vote.orElse(defaultVote);
  }

  private NavigableMap<UnsignedLong, Eth1Data> getVotesToConsider() {
    return eth1ChainCache.subMap(
        getSpecRangeLowerBound(currentVotingPeriodStartTime),
        true,
        getSpecRangeUpperBound(currentVotingPeriodStartTime),
        true);
  }

  private void prune(UnsignedLong periodStart) {
    if (eth1ChainCache.isEmpty()) return;

    while (isBlockTooOld(eth1ChainCache.firstKey(), periodStart)) {
      eth1ChainCache.remove(eth1ChainCache.firstKey());
    }
  }

  private void prune() {
    if (eth1ChainCache.isEmpty()) return;

    while (isBlockTooOld(eth1ChainCache.firstKey())) {
      eth1ChainCache.remove(eth1ChainCache.firstKey());
    }
  }

  private UnsignedLong getVotingPeriodStartTime(UnsignedLong slot) {
    UnsignedLong eth1VotingPeriodStartSlot =
        slot.minus(slot.mod(UnsignedLong.valueOf(SLOTS_PER_ETH1_VOTING_PERIOD)));
    return computeTimeAtSlot(eth1VotingPeriodStartSlot);
  }

  private static UnsignedLong getSpecRangeLowerBound(UnsignedLong currentVotingPeriodStartTime) {
    return currentVotingPeriodStartTime.minus(
        ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK).times(UnsignedLong.valueOf(2)));
  }

  private static UnsignedLong getSpecRangeUpperBound(UnsignedLong currentVotingPeriodStartTime) {
    return currentVotingPeriodStartTime.minus(ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK));
  }

  private UnsignedLong computeTimeAtSlot(UnsignedLong slot) {
    if (genesisTime.isEmpty())
      throw new RuntimeException("computeTimeAtSlot called without genesisTime being set");
    return genesisTime.get().plus(slot.times(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT)));
  }

  private static boolean isBlockTooOld(UnsignedLong blockTimestamp, UnsignedLong periodStart) {
    return blockTimestamp.compareTo(getSpecRangeLowerBound(periodStart)) < 0;
  }

  private static boolean isBlockTooOld(UnsignedLong blockTimestamp) {
    return blockTimestamp.compareTo(getCacheRangeLowerBound()) < 0;
  }

  private static Eth1Data createEth1Data(CacheEth1BlockEvent eth1BlockEvent) {
    return new Eth1Data(
        eth1BlockEvent.getDepositRoot(),
        eth1BlockEvent.getDepositCount(),
        eth1BlockEvent.getBlockHash());
  }
}
