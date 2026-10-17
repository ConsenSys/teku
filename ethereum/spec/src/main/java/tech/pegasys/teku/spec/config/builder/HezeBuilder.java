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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.config.SpecConfigHezeImpl;

public class HezeBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigGloas, SpecConfigHeze> {

  private static final Logger LOG = LogManager.getLogger();

  private Integer inclusionListDueBps;
  private Integer maxRequestInclusionList;
  private Integer minSlotsForInclusionListsRequests;
  private Integer maxTransactionsBytesPerInclusionList;

  // heze preset
  private Integer inclusionListCommitteeSize;
  private Integer maxSignedInclusionListSize;

  // EIP-8198 (quick slots) is not implemented yet. These are optional and default to the current
  // timing, and a slot duration change at the Heze fork is rejected rather than silently ignored.
  private Optional<Integer> slotDurationMsHeze = Optional.empty();
  private Optional<UInt64> minBlobDataRetentionMs = Optional.empty();

  HezeBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigHeze> build(
      final SpecConfigAndParent<SpecConfigGloas> specConfigAndParent) {
    final SpecConfigGloas gloasConfig = specConfigAndParent.specConfig();
    final int slotDurationMs = gloasConfig.getSlotDurationMillis();
    final UInt64 dataColumnSidecarsRetentionMs =
        UInt64.valueOf(gloasConfig.getMinEpochsForDataColumnSidecarsRequests())
            .times(gloasConfig.getSlotsPerEpoch())
            .times(slotDurationMs);
    final int resolvedSlotDurationMsHeze = slotDurationMsHeze.orElse(slotDurationMs);
    final UInt64 resolvedMinBlobDataRetentionMs =
        minBlobDataRetentionMs.orElse(dataColumnSidecarsRetentionMs);

    if (!getForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      checkArgument(
          resolvedSlotDurationMsHeze == slotDurationMs,
          "SLOT_DURATION_MS_HEZE (%s) must equal SLOT_DURATION_MS (%s): changing the slot duration"
              + " at the Heze fork (EIP-8198) is not supported yet",
          resolvedSlotDurationMsHeze,
          slotDurationMs);
      if (!resolvedMinBlobDataRetentionMs.equals(dataColumnSidecarsRetentionMs)) {
        LOG.warn(
            "MIN_BLOB_DATA_RETENTION_MS ({}) differs from the retention period implied by"
                + " MIN_EPOCHS_FOR_DATA_COLUMN_SIDECARS_REQUESTS ({} ms). Data column sidecars are"
                + " retained and served using MIN_EPOCHS_FOR_DATA_COLUMN_SIDECARS_REQUESTS until"
                + " EIP-8198 is supported",
            resolvedMinBlobDataRetentionMs,
            dataColumnSidecarsRetentionMs);
      }
    }

    return SpecConfigAndParent.of(
        new SpecConfigHezeImpl(
            gloasConfig,
            inclusionListDueBps,
            maxRequestInclusionList,
            minSlotsForInclusionListsRequests,
            maxTransactionsBytesPerInclusionList,
            maxSignedInclusionListSize,
            inclusionListCommitteeSize,
            resolvedSlotDurationMsHeze,
            resolvedMinBlobDataRetentionMs),
        specConfigAndParent);
  }

  public HezeBuilder inclusionListDueBps(final Integer inclusionListDueBps) {
    checkNotNull(inclusionListDueBps);
    this.inclusionListDueBps = inclusionListDueBps;
    return this;
  }

  public HezeBuilder maxRequestInclusionList(final Integer maxRequestInclusionList) {
    checkNotNull(maxRequestInclusionList);
    this.maxRequestInclusionList = maxRequestInclusionList;
    return this;
  }

  public HezeBuilder minSlotsForInclusionListsRequests(
      final Integer minSlotsForInclusionListsRequests) {
    checkNotNull(minSlotsForInclusionListsRequests);
    this.minSlotsForInclusionListsRequests = minSlotsForInclusionListsRequests;
    return this;
  }

  public HezeBuilder maxTransactionsBytesPerInclusionList(
      final Integer maxTransactionsBytesPerInclusionList) {
    checkNotNull(maxTransactionsBytesPerInclusionList);
    this.maxTransactionsBytesPerInclusionList = maxTransactionsBytesPerInclusionList;
    return this;
  }

  public HezeBuilder inclusionListCommitteeSize(final Integer inclusionListCommitteeSize) {
    checkNotNull(inclusionListCommitteeSize);
    this.inclusionListCommitteeSize = inclusionListCommitteeSize;
    return this;
  }

  public HezeBuilder maxSignedInclusionListSize(final Integer maxSignedInclusionListSize) {
    checkNotNull(maxSignedInclusionListSize);
    this.maxSignedInclusionListSize = maxSignedInclusionListSize;
    return this;
  }

  public HezeBuilder slotDurationMsHeze(final Integer slotDurationMsHeze) {
    checkNotNull(slotDurationMsHeze);
    this.slotDurationMsHeze = Optional.of(slotDurationMsHeze);
    return this;
  }

  public HezeBuilder minBlobDataRetentionMs(final UInt64 minBlobDataRetentionMs) {
    checkNotNull(minBlobDataRetentionMs);
    this.minBlobDataRetentionMs = Optional.of(minBlobDataRetentionMs);
    return this;
  }

  @Override
  public void validate() {
    defaultValuesIfRequired(this);
    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("inclusionListDueBps", inclusionListDueBps);
    constants.put("maxRequestInclusionList", maxRequestInclusionList);
    constants.put("minSlotsForInclusionListsRequests", minSlotsForInclusionListsRequests);
    constants.put("maxTransactionsBytesPerInclusionList", maxTransactionsBytesPerInclusionList);
    constants.put("inclusionListCommitteeSize", inclusionListCommitteeSize);
    constants.put("maxSignedInclusionListSize", maxSignedInclusionListSize);
    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("MAX_SIGNED_INCLUSION_LIST_SIZE", maxSignedInclusionListSize);
  }
}
