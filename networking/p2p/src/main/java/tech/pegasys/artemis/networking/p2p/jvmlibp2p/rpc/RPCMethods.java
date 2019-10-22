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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import java.util.Arrays;
import java.util.List;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksMessageRequest;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksMessageResponse;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.HelloMessage;

public class RPCMethods {
  private final RPCMessageHandler<HelloMessage, HelloMessage> hello;
  private final RPCMessageHandler<GoodbyeMessage, Void> goodbye;
  private final RPCMessageHandler<BeaconBlocksMessageRequest, BeaconBlocksMessageResponse>
      beaconBlocks;

  public RPCMethods(
      LocalMessageHandler<HelloMessage, HelloMessage> helloHandler,
      LocalMessageHandler<GoodbyeMessage, Void> goodbyeHandler,
      LocalMessageHandler<BeaconBlocksMessageRequest, BeaconBlocksMessageResponse>
          beaconBlocksHandler) {

    this.hello =
        new RPCMessageHandler<>(
            "/eth2/beacon_chain/req/hello/1/ssz",
            HelloMessage.class,
            HelloMessage.class,
            helloHandler);

    this.goodbye =
        new RPCMessageHandler<>(
                "/eth2/beacon_chain/req/goodbye/1/ssz",
                GoodbyeMessage.class,
                Void.class,
                goodbyeHandler)
            .setNotification();

    this.beaconBlocks =
        new RPCMessageHandler<>(
            "/eth2/beacon_chain/req/beacon_blocks/1/ssz",
            BeaconBlocksMessageRequest.class,
            BeaconBlocksMessageResponse.class,
            beaconBlocksHandler);
  }

  public RPCMessageHandler<HelloMessage, HelloMessage> getHello() {
    return hello;
  }

  public List<RPCMessageHandler<?, ?>> all() {
    return Arrays.asList(getHello(), goodbye, beaconBlocks);
  }
}
