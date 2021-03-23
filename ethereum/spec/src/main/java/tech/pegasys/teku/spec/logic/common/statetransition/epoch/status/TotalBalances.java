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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch.status;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;

public class TotalBalances {

  private final UInt64 effectiveBalanceIncrement;

  private final UInt64 currentEpoch;
  private final UInt64 previousEpoch;
  private final UInt64 currentEpochSourceAttesters;
  private final UInt64 currentEpochTargetAttesters;
  private final UInt64 previousEpochSourceAttesters;
  private final UInt64 previousEpochTargetAttesters;
  private final UInt64 previousEpochHeadAttesters;

  public TotalBalances(
      final SpecConfig specConfig,
      UInt64 currentEpoch,
      UInt64 previousEpoch,
      UInt64 currentEpochSourceAttesters,
      UInt64 currentEpochTargetAttesters,
      UInt64 previousEpochSourceAttesters,
      UInt64 previousEpochTargetAttesters,
      UInt64 previousEpochHeadAttesters) {
    this.effectiveBalanceIncrement = specConfig.getEffectiveBalanceIncrement();
    this.currentEpoch = currentEpoch;
    this.previousEpoch = previousEpoch;
    this.currentEpochSourceAttesters = currentEpochSourceAttesters;
    this.currentEpochTargetAttesters = currentEpochTargetAttesters;
    this.previousEpochSourceAttesters = previousEpochSourceAttesters;
    this.previousEpochTargetAttesters = previousEpochTargetAttesters;
    this.previousEpochHeadAttesters = previousEpochHeadAttesters;
  }

  public UInt64 getCurrentEpoch() {
    return currentEpoch.max(effectiveBalanceIncrement);
  }

  public UInt64 getPreviousEpoch() {
    return previousEpoch.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the current epoch that attested to
   *     the correct source (justified checkpoint).
   */
  public UInt64 getCurrentEpochSourceAttesters() {
    return currentEpochSourceAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the current epoch that attested to
   *     the correct target (epoch boundary block).
   */
  public UInt64 getCurrentEpochTargetAttesters() {
    return currentEpochTargetAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct source (justified checkpoint).
   */
  public UInt64 getPreviousEpochSourceAttesters() {
    return previousEpochSourceAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct target (epoch boundary block).
   */
  public UInt64 getPreviousEpochTargetAttesters() {
    return previousEpochTargetAttesters.max(effectiveBalanceIncrement);
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct head block at their assigned slot.
   */
  public UInt64 getPreviousEpochHeadAttesters() {
    return previousEpochHeadAttesters.max(effectiveBalanceIncrement);
  }
}
