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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.heze;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container11;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.util.Eip8015RemovedFields;

public class BeaconBlockBodyHezeImpl
    extends Container11<
        BeaconBlockBodyHezeImpl,
        SszSignature,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<SignedVoluntaryExit>,
        SyncAggregate,
        SszList<SignedBlsToExecutionChange>,
        SignedExecutionPayloadBid,
        SszList<PayloadAttestation>,
        ExecutionRequests>
    implements BeaconBlockBodyGloas {

  BeaconBlockBodyHezeImpl(
      final BeaconBlockBodySchemaHezeImpl type,
      final SszSignature randaoReveal,
      final SszBytes32 graffiti,
      final SszList<ProposerSlashing> proposerSlashings,
      final SszList<AttesterSlashing> attesterSlashings,
      final SszList<Attestation> attestations,
      final SszList<SignedVoluntaryExit> voluntaryExits,
      final SyncAggregate syncAggregate,
      final SszList<SignedBlsToExecutionChange> blsToExecutionChanges,
      final SignedExecutionPayloadBid signedExecutionPayloadBid,
      final SszList<PayloadAttestation> payloadAttestations,
      final ExecutionRequests parentExecutionRequests) {
    super(
        type,
        randaoReveal,
        graffiti,
        proposerSlashings,
        attesterSlashings,
        attestations,
        voluntaryExits,
        syncAggregate,
        blsToExecutionChanges,
        signedExecutionPayloadBid,
        payloadAttestations,
        parentExecutionRequests);
  }

  BeaconBlockBodyHezeImpl(final BeaconBlockBodySchemaHezeImpl type) {
    super(type);
  }

  BeaconBlockBodyHezeImpl(final BeaconBlockBodySchemaHezeImpl type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public static BeaconBlockBodyHezeImpl required(final BeaconBlockBody body) {
    checkArgument(
        body instanceof BeaconBlockBodyHezeImpl,
        "Expected Heze block body but got %s",
        body.getClass());
    return (BeaconBlockBodyHezeImpl) body;
  }

  @Override
  public BLSSignature getRandaoReveal() {
    return getField0().getSignature();
  }

  @Override
  public SszSignature getRandaoRevealSsz() {
    return getField0();
  }

  /** {@code eth1_data} was removed in Heze (EIP-8015). */
  @Override
  public Eth1Data getEth1Data() {
    Eip8015RemovedFields.logAccess(BlockBodyFields.ETH1_DATA);
    return Eip8015RemovedFields.EMPTY_ETH1_DATA;
  }

  @Override
  public Bytes32 getGraffiti() {
    return getField1().get();
  }

  @Override
  public SszBytes32 getGraffitiSsz() {
    return getField1();
  }

  @Override
  public SszList<ProposerSlashing> getProposerSlashings() {
    return getField2();
  }

  @Override
  public SszList<AttesterSlashing> getAttesterSlashings() {
    return getField3();
  }

  @Override
  public SszList<Attestation> getAttestations() {
    return getField4();
  }

  /** {@code deposits} was removed in Heze (EIP-8015). */
  @Override
  public SszList<Deposit> getDeposits() {
    Eip8015RemovedFields.logAccess(BlockBodyFields.DEPOSITS);
    return Eip8015RemovedFields.EMPTY_DEPOSITS;
  }

  @Override
  public SszList<SignedVoluntaryExit> getVoluntaryExits() {
    return getField5();
  }

  @Override
  public SyncAggregate getSyncAggregate() {
    return getField6();
  }

  @Override
  public SszList<SignedBlsToExecutionChange> getBlsToExecutionChanges() {
    return getField7();
  }

  @Override
  public SignedExecutionPayloadBid getSignedExecutionPayloadBid() {
    return getField8();
  }

  @Override
  public SszList<PayloadAttestation> getPayloadAttestations() {
    return getField9();
  }

  @Override
  public ExecutionRequests getParentExecutionRequests() {
    return getField10();
  }

  @Override
  public BeaconBlockBodySchemaHezeImpl getSchema() {
    return (BeaconBlockBodySchemaHezeImpl) super.getSchema();
  }
}
