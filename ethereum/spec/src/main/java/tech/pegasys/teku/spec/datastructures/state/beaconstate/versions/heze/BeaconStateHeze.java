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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;

/**
 * Heze removes {@code eth1_data}, {@code eth1_data_votes}, {@code eth1_deposit_index} and {@code
 * deposit_requests_start_index} (EIP-8015). Their accessors return empty values and log an error.
 */
public interface BeaconStateHeze extends BeaconStateGloas {

  @Override
  default Eth1Data getEth1Data() {
    Eip8015RemovedFields.logAccess(BeaconStateFields.ETH1_DATA);
    return Eip8015RemovedFields.EMPTY_ETH1_DATA;
  }

  @Override
  default SszList<Eth1Data> getEth1DataVotes() {
    Eip8015RemovedFields.logAccess(BeaconStateFields.ETH1_DATA_VOTES);
    return Eip8015RemovedFields.EMPTY_ETH1_DATA_VOTES;
  }

  @Override
  default UInt64 getEth1DepositIndex() {
    Eip8015RemovedFields.logAccess(BeaconStateFields.ETH1_DEPOSIT_INDEX);
    return Eip8015RemovedFields.EMPTY_INDEX;
  }

  @Override
  default UInt64 getDepositRequestsStartIndex() {
    Eip8015RemovedFields.logAccess(BeaconStateFields.DEPOSIT_REQUESTS_START_INDEX);
    return Eip8015RemovedFields.EMPTY_INDEX;
  }

  @Override
  MutableBeaconStateHeze createWritableCopy();
}
