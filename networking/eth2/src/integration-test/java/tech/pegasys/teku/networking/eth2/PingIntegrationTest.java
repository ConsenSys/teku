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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

public class PingIntegrationTest {
  private final Eth2NetworkFactory networkFactory = new Eth2NetworkFactory();
  private Eth2Network network1;
  private Eth2Network network2;
  private Eth2Peer peer1;
  private Eth2Peer peer2;

  @BeforeEach
  public void setUp() throws Exception {
    network1 = networkFactory.builder().startNetwork();
    network2 = networkFactory.builder().peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    peer2 = network1.getPeer(network2.getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void testPingUpdatesMetadata() throws Exception {
    Optional<Bitvector> attNets1_0 = peer1.getRemoteAttestationSubnets();
    Optional<Bitvector> attNets2_0 = peer2.getRemoteAttestationSubnets();

    assertThat(attNets1_0.isEmpty() || attNets1_0.get().getBitCount() == 0);
    assertThat(attNets2_0.isEmpty() || attNets2_0.get().getBitCount() == 0);

    MetadataMessage md1 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);
    MetadataMessage md2 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);

    UnsignedLong ping1_0 = peer1.sendPing().get(10, TimeUnit.SECONDS);
    UnsignedLong ping2_0 = peer2.sendPing().get(10, TimeUnit.SECONDS);

    assertThat(ping1_0).isEqualTo(md1.getSeqNumber());
    assertThat(ping2_0).isEqualTo(md2.getSeqNumber());

    UnsignedLong ping1_1 = peer1.sendPing().get(10, TimeUnit.SECONDS);
    UnsignedLong ping2_1 = peer2.sendPing().get(10, TimeUnit.SECONDS);

    assertThat(ping1_1).isEqualTo(md1.getSeqNumber());
    assertThat(ping2_1).isEqualTo(md2.getSeqNumber());
  }
}
