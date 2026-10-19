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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.NetworkConstants.MAX_REQUEST_LIGHT_CLIENT_UPDATES;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class LightClientUpdatesByRangeRequestMessageTest {

  @Test
  void shouldRoundTripSsz() {
    final LightClientUpdatesByRangeRequestMessage message =
        new LightClientUpdatesByRangeRequestMessage(UInt64.valueOf(3), UInt64.valueOf(5));

    final Bytes ssz = message.sszSerialize();

    assertThat(ssz.size()).isEqualTo(16);
    assertThat(LightClientUpdatesByRangeRequestMessage.SSZ_SCHEMA.sszDeserialize(ssz))
        .isEqualTo(message);
    assertThat(message.getStartPeriod()).isEqualTo(UInt64.valueOf(3));
    assertThat(message.getCount()).isEqualTo(UInt64.valueOf(5));
  }

  @Test
  void shouldCapMaximumResponseChunksAtMaxRequestLightClientUpdates() {
    assertThat(
            new LightClientUpdatesByRangeRequestMessage(UInt64.ZERO, UInt64.valueOf(5))
                .getMaximumResponseChunks())
        .isEqualTo(5);
    assertThat(
            new LightClientUpdatesByRangeRequestMessage(UInt64.ZERO, UInt64.MAX_VALUE)
                .getMaximumResponseChunks())
        .isEqualTo(MAX_REQUEST_LIGHT_CLIENT_UPDATES);
  }
}
