/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.core.epoch;

import tech.pegasys.teku.core.Deltas;
import tech.pegasys.teku.core.epoch.status.ValidatorStatus;
import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface RewardsAndPenaltiesStep {
  void apply(
      final Deltas deltas,
      final TotalBalances totalBalances,
      final UInt64 finalityDelay,
      final ValidatorStatus validator,
      final UInt64 baseReward,
      final Deltas.Delta delta);
}
