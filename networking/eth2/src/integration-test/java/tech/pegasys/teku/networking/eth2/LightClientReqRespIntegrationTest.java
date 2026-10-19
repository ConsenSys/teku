/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.RESOURCE_UNAVAILABLE;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;

public class LightClientReqRespIntegrationTest {
  private final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();
  private final LightClientUpdateStore serverStore = new LightClientUpdateStore(spec);

  private Eth2Peer serverPeer;

  @BeforeEach
  public void setUp() throws Exception {
    final NodeManager server =
        NodeManager.create(
            spec,
            asyncRunner,
            networkFactory,
            validatorKeys,
            b -> b.lightClientUpdateStore(serverStore));
    // The client needs the methods registered too, so it gets its own (empty) store
    final NodeManager client =
        NodeManager.create(
            spec,
            asyncRunner,
            networkFactory,
            validatorKeys,
            b -> b.lightClientUpdateStore(new LightClientUpdateStore(spec)));

    waitFor(client.connect(server));
    waitFor(() -> assertThat(client.network().getPeerCount()).isEqualTo(1));
    serverPeer = client.network().getPeer(server.network().getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldReturnLatestFinalityUpdate() throws Exception {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(
            UInt64.valueOf(9), UInt64.ONE, UInt64.valueOf(10));
    serverStore.addFinalityUpdate(update, Bytes32.ZERO, (slot, root) -> true);

    assertThat(serverPeer.requestLightClientFinalityUpdate().get(10, TimeUnit.SECONDS))
        .isEqualTo(update);
  }

  @Test
  public void shouldReturnResourceUnavailableWhenNoFinalityUpdate() {
    assertResourceUnavailable(serverPeer.requestLightClientFinalityUpdate());
  }

  @Test
  public void shouldReturnLatestOptimisticUpdate() throws Exception {
    final LightClientOptimisticUpdate update =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.valueOf(9), UInt64.valueOf(10));
    serverStore.addOptimisticUpdate(update, Bytes32.ZERO, (slot, root) -> true);

    assertThat(serverPeer.requestLightClientOptimisticUpdate().get(10, TimeUnit.SECONDS))
        .isEqualTo(update);
  }

  @Test
  public void shouldReturnResourceUnavailableWhenNoOptimisticUpdate() {
    assertResourceUnavailable(serverPeer.requestLightClientOptimisticUpdate());
  }

  private static void assertResourceUnavailable(final SafeFuture<?> future) {
    final Throwable error = catchThrowable(() -> future.get(10, TimeUnit.SECONDS));
    assertThat(error).hasCauseInstanceOf(RpcException.class);
    assertThat(((RpcException) error.getCause()).getResponseCode()).isEqualTo(RESOURCE_UNAVAILABLE);
  }
}
