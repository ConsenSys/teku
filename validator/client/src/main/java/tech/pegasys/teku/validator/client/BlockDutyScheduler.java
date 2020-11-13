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

package tech.pegasys.teku.validator.client;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlockDutyScheduler extends AbstractDutyScheduler {

  public BlockDutyScheduler(
      final MetricsSystem metricsSystem, final DutyLoader epochDutiesScheduler) {
    super(epochDutiesScheduler, 0);

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_block_duties_current",
        "Current number of pending block duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(DutyQueue::countDuties).sum());
  }

  @Override
  protected Bytes32 getExpectedTargetRoot(
      final Bytes32 headBlockRoot,
      final Bytes32 currentTargetRoot,
      final Bytes32 previousTargetRoot,
      final UInt64 headEpoch,
      final UInt64 currentEpoch) {
    final Bytes32 targetRoot;
    if (headEpoch.equals(currentEpoch)) {
      targetRoot = currentTargetRoot;
    } else {
      targetRoot = headBlockRoot;
    }
    return targetRoot;
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {
    notifyDutyQueue(DutyQueue::onBlockProductionDue, slot);
  }
}