/*
 * Copyright (c) 2025 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class InclusionListFactory {

  private final ExecutionLayerChannel executionLayerChannel;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Spec spec;

  public InclusionListFactory(
      final ExecutionLayerChannel executionLayerChannel,
      final CombinedChainDataClient combinedChainDataClient,
      final Spec spec) {
    this.executionLayerChannel = executionLayerChannel;
    this.combinedChainDataClient = combinedChainDataClient;
    this.spec = spec;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<Optional<InclusionList>> createInclusionList(
      final UInt64 slot, final UInt64 validatorIndex) {
    final Optional<SchemaDefinitionsHeze> maybeSchemaDefinitions =
        spec.atSlot(slot).getSchemaDefinitions().toVersionHeze();
    if (maybeSchemaDefinitions.isEmpty()) {
      return preHezeSlotFailure(slot);
    }
    final InclusionListSchema inclusionListSchema =
        maybeSchemaDefinitions.get().getInclusionListSchema();
    return combinedChainDataClient
        .getBestState()
        .orElseGet(
            () ->
                SafeFuture.failedFuture(
                    new IllegalStateException("Head state is not yet available")))
        .thenCompose(
            state -> {
              final Bytes32 dependentRoot = spec.getInclusionListDependentRoot(state, slot);
              final Bytes32 parentHash =
                  BeaconStateGloas.required(state).getLatestExecutionPayloadBid().getBlockHash();
              return executionLayerChannel
                  .engineGetInclusionList(parentHash, slot)
                  .thenApply(
                      transactions ->
                          inclusionListSchema.create(
                              slot, validatorIndex, dependentRoot, transactions))
                  .thenApply(Optional::ofNullable);
            });
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<Optional<List<Transaction>>> getInclusionList(final UInt64 slot) {
    if (spec.atSlot(slot).getMilestone().isLessThan(SpecMilestone.HEZE)) {
      return preHezeSlotFailure(slot);
    }
    return combinedChainDataClient
        .getBestState()
        .orElseGet(
            () ->
                SafeFuture.failedFuture(
                    new IllegalStateException("Head state is not yet available")))
        .thenCompose(
            state -> {
              final Bytes32 parentHash =
                  BeaconStateGloas.required(state).getLatestExecutionPayloadBid().getBlockHash();
              return executionLayerChannel
                  .engineGetInclusionList(parentHash, slot)
                  .thenApply(Optional::ofNullable);
            });
  }

  private static <T> SafeFuture<T> preHezeSlotFailure(final UInt64 slot) {
    return SafeFuture.failedFuture(
        new IllegalArgumentException(
            "Inclusion lists are not available for slot " + slot + " before the Heze fork"));
  }
}
