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

package org.ethereum.beacon.chain.storage;

import java.util.List;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.types.SlotNumber;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

public interface BeaconBlockStorage extends HashKeyStorage<Hash32, BeaconBlock> {
  /** @return maxStoredSlot or {@link UInt64#MAX_VALUE} if storage is empty */
  SlotNumber getMaxSlot();

  default boolean isEmpty() {
    return getMaxSlot().equals(UInt64.MAX_VALUE);
  }

  List<Hash32> getSlotBlocks(SlotNumber slot);

  /**
   * Searches for all children with limit slot distance from parent
   *
   * @param parent Start block hash
   * @param limit Slot limit for forward children search
   * @return list of children
   */
  List<BeaconBlock> getChildren(Hash32 parent, int limit);
}
