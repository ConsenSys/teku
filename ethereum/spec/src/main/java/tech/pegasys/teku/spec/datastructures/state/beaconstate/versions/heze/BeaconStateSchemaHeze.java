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

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * The Heze state removes {@code eth1_data}, {@code eth1_data_votes}, {@code eth1_deposit_index} and
 * {@code deposit_requests_start_index} (EIP-8015). Their slots are gaps, so the remaining fields
 * keep their tree positions: {@code active_fields(width=46, gaps=(8, 9, 10, 28))}.
 */
public class BeaconStateSchemaHeze extends BeaconStateSchemaGloas {

  private static final Set<String> REMOVED_FIELD_NAMES =
      Stream.of(
              BeaconStateFields.ETH1_DATA,
              BeaconStateFields.ETH1_DATA_VOTES,
              BeaconStateFields.ETH1_DEPOSIT_INDEX,
              BeaconStateFields.DEPOSIT_REQUESTS_START_INDEX)
          .map(BeaconStateFields::getSszFieldName)
          .collect(Collectors.toSet());

  private BeaconStateSchemaHeze(final List<SszField> fields) {
    super("BeaconStateHeze", activeFields(fields), fields);
  }

  public static BeaconStateSchemaHeze create(
      final SpecConfigHeze specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaHeze(getFields(specConfig, schemaRegistry));
  }

  private static List<SszField> getFields(
      final SpecConfigHeze specConfig, final SchemaRegistry schemaRegistry) {
    return Stream.concat(
            BeaconStateFields.getCommonFields(specConfig).stream(),
            getUniqueFields(specConfig, schemaRegistry).stream())
        .filter(field -> !REMOVED_FIELD_NAMES.contains(field.getName()))
        .sorted(Comparator.comparingInt(SszField::getIndex))
        .toList();
  }

  /** Each field keeps its Gloas index, so the removed fields' indices become gaps. */
  private static boolean[] activeFields(final List<SszField> fields) {
    final boolean[] activeFields = new boolean[PTC_WINDOW_INDEX + 1];
    fields.forEach(field -> activeFields[field.getIndex()] = true);
    return activeFields;
  }

  /** {@code eth1_data_votes} was removed in Heze (EIP-8015). */
  @Override
  public SszListSchema<Eth1Data, ?> getEth1DataVotesSchema() {
    Eip8015RemovedFields.logAccess(BeaconStateFields.ETH1_DATA_VOTES);
    return Eip8015RemovedFields.EMPTY_ETH1_DATA_VOTES.getSchema();
  }

  @Override
  public MutableBeaconStateHeze createBuilder() {
    return new MutableBeaconStateHezeImpl(new BeaconStateHezeImpl(this), true);
  }

  @Override
  public BeaconStateHeze createEmpty() {
    return new BeaconStateHezeImpl(this);
  }

  @Override
  public BeaconStateHeze createEmptyWithTransitionCachesFrom(final BeaconState sourceState) {
    return new BeaconStateHezeImpl(
        this,
        BeaconStateCache.getTransitionCaches(sourceState).copy(),
        SlotCaches.createNewEmpty());
  }

  @Override
  public BeaconStateHezeImpl createFromBackingNode(final TreeNode node) {
    return new BeaconStateHezeImpl(this, node);
  }
}
