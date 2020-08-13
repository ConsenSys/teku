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

package tech.pegasys.teku.core;

import static org.assertj.core.util.Preconditions.checkNotNull;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.LocalMessageSignerService;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.UnprotectedSigner;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AggregateGenerator {
  private final AttestationGenerator attestationGenerator;
  private final List<BLSKeyPair> validatorKeys;

  public AggregateGenerator(final List<BLSKeyPair> validatorKeys) {
    attestationGenerator = new AttestationGenerator(validatorKeys);
    this.validatorKeys = validatorKeys;
  }

  public AttestationGenerator getAttestationGenerator() {
    return attestationGenerator;
  }

  public Generator generator() {
    return new Generator();
  }

  public SignedAggregateAndProof validAggregateAndProof(final BeaconBlockAndState blockAndState) {
    return generator().blockAndState(blockAndState).generate();
  }

  public SignedAggregateAndProof validAggregateAndProof(
      final BeaconBlockAndState blockAndState, final UInt64 slot) {
    return generator().blockAndState(blockAndState).slot(slot).generate();
  }

  private Signer getSignerForValidatorIndex(final int validatorIndex) {
    return new UnprotectedSigner(
        new LocalMessageSignerService(
            validatorKeys.get(validatorIndex), DelayedExecutorAsyncRunner.create()));
  }

  public class Generator {
    private BeaconBlockAndState blockAndState;
    private Optional<UInt64> aggregatorIndex = Optional.empty();
    private Optional<UInt64> slot = Optional.empty();
    private Optional<Attestation> aggregate = Optional.empty();
    private Optional<BLSSignature> selectionProof = Optional.empty();
    private Optional<UInt64> committeeIndex = Optional.empty();

    public Generator blockAndState(final BeaconBlockAndState blockAndState) {
      this.blockAndState = blockAndState;
      return this;
    }

    public Generator slot(final UInt64 slot) {
      this.slot = Optional.of(slot);
      return this;
    }

    public Generator aggregatorIndex(final UInt64 aggregatorIndex) {
      this.aggregatorIndex = Optional.of(aggregatorIndex);
      return this;
    }

    public Generator aggregate(final Attestation aggregate) {
      this.aggregate = Optional.of(aggregate);
      return this;
    }

    public Generator committeeIndex(final UInt64 committeeIndex) {
      this.committeeIndex = Optional.of(committeeIndex);
      return this;
    }

    public Generator selectionProof(final BLSSignature selectionProof) {
      this.selectionProof = Optional.of(selectionProof);
      return this;
    }

    public SignedAggregateAndProof generate() {
      checkNotNull(blockAndState, "Missing block and state");
      final UInt64 slot = this.slot.orElseGet(blockAndState::getSlot);
      final Attestation aggregate = this.aggregate.orElseGet(() -> createAttestation(slot));

      return this.aggregatorIndex
          .map(index -> generateWithFixedAggregatorIndex(slot, aggregate, index))
          .orElseGet(() -> generateWithAnyValidAggregatorIndex(aggregate));
    }

    private SignedAggregateAndProof generateWithAnyValidAggregatorIndex(
        final Attestation aggregate) {
      final BeaconState state = blockAndState.getState();
      final List<Integer> beaconCommittee =
          CommitteeUtil.get_beacon_committee(
              state, aggregate.getData().getSlot(), aggregate.getData().getIndex());
      for (int validatorIndex : beaconCommittee) {
        final Optional<BLSSignature> maybeSelectionProof =
            createValidSelectionProof(validatorIndex, state, aggregate);
        if (maybeSelectionProof.isPresent()) {
          return generate(aggregate, UInt64.valueOf(validatorIndex), maybeSelectionProof.get());
        }
      }

      throw new NoSuchElementException("No valid aggregate possible");
    }

    private SignedAggregateAndProof generateWithFixedAggregatorIndex(
        final UInt64 slot, final Attestation aggregate, final UInt64 aggregatorIndex) {
      final BeaconState state = blockAndState.getState();
      final BLSSignature validSelectionProof =
          createSelectionProof(aggregatorIndex.intValue(), state, slot);
      return generate(aggregate, aggregatorIndex, validSelectionProof);
    }

    private SignedAggregateAndProof generate(
        final Attestation aggregate,
        final UInt64 aggregatorIndex,
        final BLSSignature validSelectionProof) {
      final BLSSignature selectionProof = this.selectionProof.orElse(validSelectionProof);
      final AggregateAndProof aggregateAndProof =
          new AggregateAndProof(aggregatorIndex, aggregate, selectionProof);
      return createSignedAggregateAndProof(
          aggregatorIndex, aggregateAndProof, blockAndState.getState());
    }

    private Attestation createAttestation(final UInt64 slot) {
      return committeeIndex
          .map(committeeIndex -> createAttestationForCommittee(slot, committeeIndex))
          .orElseGet(() -> attestationGenerator.validAttestation(blockAndState, slot));
    }

    private Attestation createAttestationForCommittee(
        final UInt64 slot, final UInt64 committeeIndex) {
      return attestationGenerator
          .streamAttestations(blockAndState, slot)
          .filter(attestation -> attestation.getData().getIndex().equals(committeeIndex))
          .findFirst()
          .orElseThrow();
    }

    private Optional<BLSSignature> createValidSelectionProof(
        final int validatorIndex, final BeaconState state, final Attestation attestation) {
      final UInt64 slot = attestation.getData().getSlot();
      final UInt64 committeeIndex = attestation.getData().getIndex();
      final List<Integer> beaconCommittee =
          CommitteeUtil.get_beacon_committee(state, slot, committeeIndex);
      final int aggregatorModulo = CommitteeUtil.getAggregatorModulo(beaconCommittee.size());
      final BLSSignature selectionProof = createSelectionProof(validatorIndex, state, slot);
      if (CommitteeUtil.isAggregator(selectionProof, aggregatorModulo)) {
        return Optional.of(selectionProof);
      }
      return Optional.empty();
    }

    private BLSSignature createSelectionProof(
        final int validatorIndex, final BeaconState state, final UInt64 slot) {
      return getSignerForValidatorIndex(validatorIndex)
          .signAggregationSlot(slot, state.getForkInfo())
          .join();
    }

    private SignedAggregateAndProof createSignedAggregateAndProof(
        final UInt64 aggregatorIndex,
        final AggregateAndProof aggregateAndProof,
        final BeaconState state) {
      final BLSSignature aggregateSignature =
          getSignerForValidatorIndex(aggregatorIndex.intValue())
              .signAggregateAndProof(aggregateAndProof, state.getForkInfo())
              .join();
      return new SignedAggregateAndProof(aggregateAndProof, aggregateSignature);
    }
  }
}
