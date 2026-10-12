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

package tech.pegasys.teku.spec.logic.versions.heze.block;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.gloas.block.BlockProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionRequestsProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BlockProcessorHeze extends BlockProcessorGloas {

  private final ExecutionRequestsDataCodec executionRequestsDataCodec;

  public BlockProcessorHeze(
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final OperationSignatureVerifier operationSignatureVerifier,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtilGloas attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final OperationValidator operationValidator,
      final SchemaDefinitionsGloas schemaDefinitions,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final ExecutionRequestsDataCodec executionRequestsDataCodec,
      final ExecutionRequestsProcessorGloas executionRequestsProcessor) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        syncCommitteeUtil,
        beaconStateAccessors,
        beaconStateMutators,
        operationSignatureVerifier,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        operationValidator,
        schemaDefinitions,
        withdrawalsHelpers,
        executionRequestsDataCodec,
        executionRequestsProcessor);
    this.executionRequestsDataCodec = executionRequestsDataCodec;
  }

  /** {@code process_eth1_data} was removed in Heze (EIP-8015). */
  @Override
  protected void processEth1Data(final MutableBeaconState state, final BeaconBlockBody body) {}

  /** The Heze block body has no {@code deposits} (EIP-8015). */
  @Override
  protected void verifyOutstandingDepositsAreProcessed(
      final BeaconState state, final BeaconBlockBody body) {}

  /** The Heze block body has no {@code deposits} to apply (EIP-8015). */
  @Override
  protected SszList<Deposit> getDepositsForBlsChangeRetry(final BeaconBlockBody body) {
    return Eip8015RemovedFields.EMPTY_DEPOSITS;
  }

  /** The Heze block body has no {@code deposits} (EIP-8015). */
  @Override
  protected void processBodyDeposits(final MutableBeaconState state, final BeaconBlockBody body) {}

  /**
   * Only used to build genesis states, which have no {@code eth1_deposit_index} in Heze (EIP-8015).
   */
  @Override
  public void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Optional<Object2IntMap<BLSPublicKey>> maybePubkeyToIndexMap,
      final boolean signatureAlreadyVerified) {
    applyDeposit(
        state,
        deposit.getData().getPubkey(),
        deposit.getData().getWithdrawalCredentials(),
        deposit.getData().getAmount(),
        deposit.getData().getSignature(),
        maybePubkeyToIndexMap,
        signatureAlreadyVerified);
  }

  @Override
  public NewPayloadRequest computeNewPayloadRequest(
      final BeaconState state,
      final BeaconBlockBody beaconBlockBody,
      final Optional<List<InclusionList>> inclusionLists)
      throws BlockProcessingException {
    final ExecutionPayload executionPayload = extractExecutionPayload(beaconBlockBody);
    final SszList<SszKZGCommitment> blobKzgCommitments = extractBlobKzgCommitments(beaconBlockBody);
    final List<VersionedHash> versionedHashes =
        blobKzgCommitments.stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .toList();
    final Bytes32 parentBeaconBlockRoot = state.getLatestBlockHeader().getParentRoot();
    final ExecutionRequests executionRequests =
        beaconBlockBody
            .getOptionalExecutionRequests()
            .orElseThrow(() -> new BlockProcessingException("Execution requests expected"));
    return new NewPayloadRequest(
        executionPayload,
        versionedHashes,
        parentBeaconBlockRoot,
        executionRequestsDataCodec.encode(executionRequests),
        inclusionLists.map(this::getInclusionListTransactions).orElse(Collections.emptyList()));
  }

  private List<Transaction> getInclusionListTransactions(final List<InclusionList> inclusionLists) {
    return inclusionLists.stream()
        .map(InclusionList::getTransactions)
        .flatMap(transactions -> transactions.stream())
        .distinct()
        .toList();
  }
}
