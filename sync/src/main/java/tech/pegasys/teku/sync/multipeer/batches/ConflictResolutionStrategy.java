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

package tech.pegasys.teku.sync.multipeer.batches;

import tech.pegasys.teku.networking.eth2.peers.SyncSource;

public interface ConflictResolutionStrategy {

  /**
   * Verify the contents of the specified batch and apply any reputation changes required.
   *
   * <p>If the batch is found to be invalid, call {@link Batch#markAsInvalid()}, otherwise call
   * {@link Batch#markFirstBlockConfirmed()} and/or {@link Batch#markLastBlockConfirmed()} as
   * appropriate.
   *
   * @param batch the batch to verify
   * @param originalSource the {@link SyncSource} that original provided the data for the batch
   */
  void verifyBatch(Batch batch, SyncSource originalSource);
}
