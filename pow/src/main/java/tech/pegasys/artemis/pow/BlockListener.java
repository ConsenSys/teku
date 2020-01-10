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

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import io.reactivex.disposables.Disposable;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.artemis.pow.event.Eth1BlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

public class BlockListener {

  private final Disposable newBlockSubscription;

  public BlockListener(
      Web3j web3j, EventBus eventBus, DepositContractListener depositContractListener) {
    this.newBlockSubscription =
        web3j
            .blockFlowable(false)
            .subscribe(
                block -> {
                  BigInteger cacheBlockNumber =
                      block
                          .getBlock()
                          .getNumber()
                          .subtract(BigInteger.valueOf(Constants.ETH1_CACHE_BUFFER));

                  SafeFuture<EthBlock> blockFuture =
                      SafeFuture.of(
                          web3j
                              .ethGetBlockByNumber(
                                  DefaultBlockParameter.valueOf(cacheBlockNumber), false)
                              .sendAsync());

                  SafeFuture<UnsignedLong> countFuture =
                      SafeFuture.of(depositContractListener.getDepositCount(cacheBlockNumber));
                  SafeFuture<Bytes32> rootFuture =
                      SafeFuture.of(depositContractListener.getDepositRoot(cacheBlockNumber));

                  SafeFuture.allOf(blockFuture, countFuture, rootFuture)
                      .finish(
                          () -> {
                            EthBlock.Block eth1Block = blockFuture.join().getBlock();
                            Bytes32 eth1BlockHash = Bytes32.fromHexString(eth1Block.getHash());
                            UnsignedLong eth1BlockTimestamp =
                                UnsignedLong.valueOf(eth1Block.getTimestamp());
                            UnsignedLong eth1BlockNumber =
                                UnsignedLong.valueOf(eth1Block.getNumber());
                            eventBus.post(
                                new Eth1BlockEvent(
                                    eth1BlockNumber,
                                    eth1BlockHash,
                                    eth1BlockTimestamp,
                                    rootFuture.join(),
                                    countFuture.join()));
                          });
                });
  }

  public void stop() {
    newBlockSubscription.dispose();
  }
}
