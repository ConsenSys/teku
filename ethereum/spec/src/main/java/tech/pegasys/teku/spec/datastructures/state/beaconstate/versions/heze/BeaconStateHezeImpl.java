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

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloasImpl;

public class BeaconStateHezeImpl extends BeaconStateGloasImpl implements BeaconStateHeze {

  BeaconStateHezeImpl(final BeaconStateSchemaHeze schema) {
    super(schema);
  }

  BeaconStateHezeImpl(
      final BeaconStateSchemaHeze schema,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    super(schema, transitionCaches, slotCaches);
  }

  BeaconStateHezeImpl(
      final SszCompositeSchema<?> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    super(type, backingNode, cache, transitionCaches, slotCaches);
  }

  BeaconStateHezeImpl(final BeaconStateSchemaHeze type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public MutableBeaconStateHeze createWritableCopy() {
    return new MutableBeaconStateHezeImpl(this);
  }
}
