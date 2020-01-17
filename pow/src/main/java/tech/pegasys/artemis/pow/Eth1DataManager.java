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

package tech.pegasys.artemis.pow;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.ETH1_REQUEST_BUFFER;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_ETH1_BLOCK;
import static tech.pegasys.artemis.util.config.Constants.SECONDS_PER_SLOT;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.event.CacheEth1BlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;

/*

Eth1Data management strays from the spec to enable a cache for quick access to
Eth1Data without having spiky batches of requests. Below is the definition of how we
define such a robust cache that is the superset of the range defined by the spec.

Definitions:
  t: current time
  Request Buffer: the time it will take for potential eth1 data request to complete

Constants:
  Eth1 Follow Distance Time = Seconds Per Eth1 Block * Eth1 Follow Distance
  Slots Per Eth1 Voting Period Time = Seconds Per Slot * Slots Per Eth1 Voting Period

cache range =
((t - (Slots Per Eth1 Voting Period Time) - (Eth1 Follow Distance Time * 2),
(t - (Eth1 Follow Distance Time) + Request Buffer))

At startup: (implemented in this class)
  - Find the cache range
  - Search Eth1 blocks to find blocks in the cache range (pseudo-code defined below)

On every Slot Event: (implemented in this class)
  - Get the latest block number you have
  - Calculate upper bound, i.e.  (t - (Eth1 Follow Distance Time) + Request Buffer))
  - Request blocks from i = 0 to  i = to infinity, until latest block number + i’s timestamp
  is greater than the upper bound

On every VotingPeriodStart change: (implemented in Eth1DataCache)
  - Prune anything that is before than:
  ((t - (Slots Per Eth1 Voting Period Time - One Slot Time) - (Eth1 Follow Distance Time * 2)

Search Eth1 Blocks to find blocks in the cache range:
  1) Get the latest block’s time stamp and block number
  2) rcr_average = ((rcr_lower_bound + rcr_upper_bound) / 2)
  3) time_diff = latest_block_timestamp - rcr_average
  4) seconds_per_eth1_block = SECONDS_PER_ETH1_BLOCK
  5) block_number_diff = time_diff / seconds_per_eth1_block
  6) block_number = latest_block_number - block_number_diff
  7) block_timestamp = getEthBlock(block_number).timestamp
  8) if isTimestampInRCR(block_timestamp):
      - go in both directions until you’re not in the range
      - post each block to event bus
     else:
      - actual_time_diff = latest_block_timestamp - block_timestamp
      - seconds_per_eth1_block = block_number_diff / actual_time_diff
      - go back to step 5

 */

public class Eth1DataManager {

  private static final Logger LOG = LogManager.getLogger();

  private final Web3j web3j;
  private final DepositContractListener depositContractListener;
  private final EventBus eventBus;

  private AtomicReference<EthBlock.Block> latestBlockReference = new AtomicReference<>();

  private enum StartupLogicStates {
    SUCCESSFULLY_COMPLETED,
    UNSUCCESSFULLY_COMPLETED,
    UNABLE_TO_EXPLORE_BLOCKS,
    DONE_EXPLORING,
  }

  public Eth1DataManager(
      Web3j web3j, EventBus eventBus, DepositContractListener depositContractListener) {
    this.web3j = web3j;
    this.depositContractListener = depositContractListener;
    this.eventBus = eventBus;
    eventBus.register(this);

    runCacheStartupLogic()
        .finish(
            (result) -> {
              if (!result.equals(StartupLogicStates.SUCCESSFULLY_COMPLETED)) {
                throw new RuntimeException("Eth1DataManager unable to fill cache at startup");
              }
            });
  }

  @Subscribe
  public void onTick(Date date) {
    // Fetch new Eth1 blocks every SECONDS_PER_ETH1_BLOCK seconds.
    // (can't use slot events here as an approximation due to this needing to be run pre-genesis)
    if (!hasBeenApproximately(SECONDS_PER_ETH1_BLOCK, date)) {
      return;
    }

    EthBlock.Block latestBlock = latestBlockReference.get();
    UnsignedLong latestTimestamp = UnsignedLong.valueOf(latestBlock.getTimestamp());

    // Don't get newer blocks if the timestamp of the last block fetched is
    // still higher than the range upper bound
    if (latestTimestamp.compareTo(getCacheRangeUpperBound()) > 0) {
      return;
    }

    UnsignedLong latestBlockNumber = UnsignedLong.valueOf(latestBlock.getNumber());
    exploreBlocksInDirection(latestBlockNumber, true)
        .finish(
            res -> {
              if (!res.equals(StartupLogicStates.DONE_EXPLORING)) {
                LOG.warn("Failed to import new eth1 blocks");
              }
            });
  }

  private SafeFuture<StartupLogicStates> runCacheStartupLogic() {
    UnsignedLong cacheRangeLowerBound = getCacheRangeLowerBound();
    UnsignedLong cacheRangerUpperBound = getCacheRangeUpperBound();

    UnsignedLong cacheMidRange =
        cacheRangerUpperBound.plus(cacheRangeLowerBound).dividedBy(UnsignedLong.valueOf(2));

    SafeFuture<EthBlock> latestEthBlockFuture = getLatestEth1BlockFuture();

    SafeFuture<UnsignedLong> latestBlockTimestampFuture =
        getBlockTimestampFuture(latestEthBlockFuture);
    SafeFuture<UnsignedLong> latestBlockNumberFuture = getBlockNumberFuture(latestEthBlockFuture);

    SafeFuture<UnsignedLong> blockNumberDiffFuture =
        getBlockNumberDiffWithMidRangeBlock(
            latestBlockTimestampFuture,
            SafeFuture.completedFuture(SECONDS_PER_ETH1_BLOCK),
            cacheMidRange);

    SafeFuture<EthBlock> blockFuture =
        getMidRangeBlock(latestBlockNumberFuture, blockNumberDiffFuture);

    return blockFuture
        .thenCompose(
            eth1block -> {
              EthBlock.Block block = eth1block.getBlock();
              UnsignedLong timestamp = UnsignedLong.valueOf(block.getTimestamp());
              SafeFuture<EthBlock> middleBlockFuture = blockFuture;
              if (!isTimestampInRange(timestamp)) {

                SafeFuture<UnsignedLong> realSecondsPerEth1BlockFuture =
                    calculateRealSecondsPerEth1BlockFuture(
                        latestBlockTimestampFuture,
                        blockNumberDiffFuture,
                        SafeFuture.completedFuture(timestamp));

                SafeFuture<UnsignedLong> newBlockNumberDiffFuture =
                    getBlockNumberDiffWithMidRangeBlock(
                        latestBlockTimestampFuture, realSecondsPerEth1BlockFuture, cacheMidRange);

                middleBlockFuture =
                    getMidRangeBlock(latestBlockNumberFuture, newBlockNumberDiffFuture);
              }
              return middleBlockFuture;
            })
        .thenCompose(
            middleBlock -> {
              EthBlock.Block block = middleBlock.getBlock();
              UnsignedLong middleBlockNumber = UnsignedLong.valueOf(block.getNumber());
              postCacheEth1BlockEvent(middleBlockNumber, block).reportExceptions();
              SafeFuture<StartupLogicStates> exploreUpResultFuture =
                  exploreBlocksInDirection(middleBlockNumber, true);
              SafeFuture<StartupLogicStates> exploreDownResultFuture =
                  exploreBlocksInDirection(middleBlockNumber, false);
              return SafeFuture.allOf(exploreUpResultFuture, exploreDownResultFuture)
                  .thenApply(
                      done -> {
                        StartupLogicStates exploreUpResult = exploreUpResultFuture.getNow(null);
                        checkNotNull(exploreUpResult);

                        StartupLogicStates exploreDownResult = exploreDownResultFuture.getNow(null);
                        checkNotNull(exploreDownResult);

                        if (exploreDownResult.equals(StartupLogicStates.DONE_EXPLORING)
                            && exploreUpResult.equals(StartupLogicStates.DONE_EXPLORING)) {
                          return StartupLogicStates.SUCCESSFULLY_COMPLETED;
                        } else {
                          return StartupLogicStates.UNSUCCESSFULLY_COMPLETED;
                        }
                      });
            });
  }

  private SafeFuture<StartupLogicStates> exploreBlocksInDirection(
      UnsignedLong blockNumber, final boolean isDirectionUp) {
    blockNumber =
        isDirectionUp ? blockNumber.plus(UnsignedLong.ONE) : blockNumber.minus(UnsignedLong.ONE);
    SafeFuture<EthBlock> blockFuture = getEth1BlockFuture(blockNumber);
    UnsignedLong finalBlockNumber = blockNumber;
    return blockFuture
        .thenCompose(
            ethBlock -> {
              EthBlock.Block block = ethBlock.getBlock();
              if (isDirectionUp) latestBlockReference.set(block);
              UnsignedLong timestamp = UnsignedLong.valueOf(block.getTimestamp());
              postCacheEth1BlockEvent(finalBlockNumber, block).reportExceptions();
              if (isTimestampInRange(timestamp)) {
                return exploreBlocksInDirection(finalBlockNumber, isDirectionUp);
              }
              return SafeFuture.completedFuture(StartupLogicStates.DONE_EXPLORING);
            })
        .exceptionally(err -> StartupLogicStates.UNABLE_TO_EXPLORE_BLOCKS);
  }

  private SafeFuture<UnsignedLong> calculateRealSecondsPerEth1BlockFuture(
      SafeFuture<UnsignedLong> latestBlockTimestampFuture,
      SafeFuture<UnsignedLong> blockNumberDiffFuture,
      SafeFuture<UnsignedLong> blockTimestampFuture) {
    return SafeFuture.allOf(latestBlockTimestampFuture, blockNumberDiffFuture, blockTimestampFuture)
        .thenApply(
            done -> {
              UnsignedLong blockTimestamp = blockTimestampFuture.getNow(null);
              checkNotNull(blockTimestamp);
              UnsignedLong blockNumberDiff = blockNumberDiffFuture.getNow(null);
              checkNotNull(blockNumberDiff);
              UnsignedLong latestBlockTimestamp = latestBlockTimestampFuture.getNow(null);
              checkNotNull(latestBlockTimestamp);

              UnsignedLong actual_time_diff = latestBlockTimestamp.minus(blockTimestamp);
              return blockNumberDiff.dividedBy(actual_time_diff);
            });
  }

  private SafeFuture<EthBlock> getMidRangeBlock(
      SafeFuture<UnsignedLong> latestBlockNumberFuture,
      SafeFuture<UnsignedLong> blockNumberDiffFuture) {
    return SafeFuture.allOf(latestBlockNumberFuture, blockNumberDiffFuture)
        .thenCompose(
            done -> {
              UnsignedLong latestBlockNumber = latestBlockNumberFuture.getNow(null);
              checkNotNull(latestBlockNumber);
              UnsignedLong blockNumberDiff = blockNumberDiffFuture.getNow(null);
              checkNotNull(blockNumberDiff);

              return getEth1BlockFuture(latestBlockNumber.minus(blockNumberDiff));
            });
  }

  private static boolean isTimestampInRange(UnsignedLong timestamp) {
    return timestamp.compareTo(getCacheRangeLowerBound()) >= 0
        && timestamp.compareTo(getCacheRangeUpperBound()) <= 0;
  }

  private SafeFuture<UnsignedLong> getBlockTimestampFuture(SafeFuture<EthBlock> blockFuture) {
    return blockFuture.thenApply(
        ethBlock -> UnsignedLong.valueOf(ethBlock.getBlock().getTimestamp()));
  }

  private SafeFuture<UnsignedLong> getBlockNumberFuture(SafeFuture<EthBlock> blockFuture) {
    return blockFuture.thenApply(ethBlock -> UnsignedLong.valueOf(ethBlock.getBlock().getNumber()));
  }

  private SafeFuture<UnsignedLong> getBlockNumberDiffWithMidRangeBlock(
      SafeFuture<UnsignedLong> latestBlockTimestampFuture,
      SafeFuture<UnsignedLong> secondsPerEth1BlockFuture,
      UnsignedLong rcrAverage) {
    return SafeFuture.allOf(latestBlockTimestampFuture, secondsPerEth1BlockFuture)
        .thenApply(
            done -> {
              UnsignedLong secondsPerEth1Block = secondsPerEth1BlockFuture.getNow(null);
              checkNotNull(secondsPerEth1Block);

              UnsignedLong latestBlockTimestamp = latestBlockTimestampFuture.getNow(null);
              checkNotNull(latestBlockTimestamp);

              if (latestBlockTimestamp.compareTo(rcrAverage) < 0) {
                throw new RuntimeException(
                    "Latest block timestamp is less than the cache mid-range");
              }
              UnsignedLong timeDiff = latestBlockTimestamp.minus(rcrAverage);
              return timeDiff.dividedBy(secondsPerEth1Block);
            });
  }

  private SafeFuture<EthBlock> getEth1BlockFuture(UnsignedLong blockNumber) {
    DefaultBlockParameter blockParameter =
        DefaultBlockParameter.valueOf(blockNumber.bigIntegerValue());
    return getEth1BlockFuture(blockParameter);
  }

  private SafeFuture<EthBlock> getEth1BlockFuture(DefaultBlockParameter blockParameter) {
    return SafeFuture.of(web3j.ethGetBlockByNumber(blockParameter, false).sendAsync());
  }

  private SafeFuture<EthBlock> getLatestEth1BlockFuture() {
    DefaultBlockParameter blockParameter = DefaultBlockParameterName.LATEST;
    return getEth1BlockFuture(blockParameter);
  }

  private SafeFuture<Void> postCacheEth1BlockEvent(UnsignedLong blockNumber, EthBlock.Block block) {
    SafeFuture<UnsignedLong> countFuture =
        SafeFuture.of(depositContractListener.getDepositCount(blockNumber));
    SafeFuture<Bytes32> rootFuture =
        SafeFuture.of(depositContractListener.getDepositRoot(blockNumber));

    return SafeFuture.allOf(countFuture, rootFuture)
        .thenRun(
            () -> {
              Bytes32 root = rootFuture.getNow(null);
              checkNotNull(root);

              UnsignedLong count = countFuture.getNow(null);
              checkNotNull(count);

              Bytes32 eth1BlockHash = Bytes32.fromHexString(block.getHash());
              UnsignedLong eth1BlockTimestamp = UnsignedLong.valueOf(block.getTimestamp());
              UnsignedLong eth1BlockNumber = UnsignedLong.valueOf(block.getNumber());

              eventBus.post(
                  new CacheEth1BlockEvent(
                      eth1BlockNumber, eth1BlockHash, eth1BlockTimestamp, root, count));
            });
  }

  public static UnsignedLong getCacheRangeLowerBound() {
    UnsignedLong current_time = UnsignedLong.valueOf(Instant.now().getEpochSecond());
    return current_time
        .minus(UnsignedLong.valueOf(SLOTS_PER_ETH1_VOTING_PERIOD * SECONDS_PER_SLOT))
        .minus(ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK).times(UnsignedLong.valueOf(2)));
  }

  public static UnsignedLong getCacheRangeUpperBound() {
    UnsignedLong current_time = UnsignedLong.valueOf(Instant.now().getEpochSecond());
    return current_time
        .minus(ETH1_FOLLOW_DISTANCE.times(SECONDS_PER_ETH1_BLOCK))
        .plus(ETH1_REQUEST_BUFFER);
  }

  public static boolean hasBeenApproximately(UnsignedLong seconds, Date date) {
    return UnsignedLong.valueOf(date.getTime())
        .mod(SECONDS_PER_ETH1_BLOCK)
        .equals(UnsignedLong.ZERO);
  }
}
