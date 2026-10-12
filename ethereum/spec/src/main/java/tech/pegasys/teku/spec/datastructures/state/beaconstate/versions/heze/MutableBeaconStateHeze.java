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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.heze;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;

/** Writes to the fields removed by EIP-8015 do nothing and log an error. */
public interface MutableBeaconStateHeze extends MutableBeaconStateGloas, BeaconStateHeze {

  @Override
  default void setEth1Data(final Eth1Data eth1Data) {
    Eip8015RemovedFields.logAccess(BeaconStateFields.ETH1_DATA);
  }

  @Override
  default SszMutableList<Eth1Data> getEth1DataVotes() {
    Eip8015RemovedFields.logAccess(BeaconStateFields.ETH1_DATA_VOTES);
    return Eip8015RemovedFields.EMPTY_ETH1_DATA_VOTES.createWritableCopy();
  }

  @Override
  default void setEth1DataVotes(final SszList<Eth1Data> eth1DataList) {
    Eip8015RemovedFields.logAccess(BeaconStateFields.ETH1_DATA_VOTES);
  }

  @Override
  default void setEth1DepositIndex(final UInt64 eth1DepositIndex) {
    Eip8015RemovedFields.logAccess(BeaconStateFields.ETH1_DEPOSIT_INDEX);
  }

  @Override
  default void setDepositRequestsStartIndex(final UInt64 depositRequestsStartIndex) {
    Eip8015RemovedFields.logAccess(BeaconStateFields.DEPOSIT_REQUESTS_START_INDEX);
  }

  @Override
  BeaconStateHeze commitChanges();

  @Override
  MutableBeaconStateHeze createWritableCopy();
}
