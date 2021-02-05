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

package tech.pegasys.teku.datastructures.blocks;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.util.SpecDependent;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container8;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema8;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

/** A Beacon block body */
public class BeaconBlockBody
    extends Container8<
        BeaconBlockBody,
        SszVector<SszByte>,
        Eth1Data,
        SszBytes32,
        SszList<ProposerSlashing>,
        SszList<AttesterSlashing>,
        SszList<Attestation>,
        SszList<Deposit>,
        SszList<SignedVoluntaryExit>> {

  public static class BeaconBlockBodyType
      extends ContainerSchema8<
          BeaconBlockBody,
          SszVector<SszByte>,
          Eth1Data,
          SszBytes32,
          SszList<ProposerSlashing>,
          SszList<AttesterSlashing>,
          SszList<Attestation>,
          SszList<Deposit>,
          SszList<SignedVoluntaryExit>> {

    public BeaconBlockBodyType() {
      super(
          "BeaconBlockBody",
          namedSchema("randao_reveal", SszComplexSchemas.BYTES_96_SCHEMA),
          namedSchema("eth1_data", Eth1Data.TYPE),
          namedSchema("graffiti", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema(
              "proposer_slashings",
              new SszListSchema<>(ProposerSlashing.TYPE, Constants.MAX_PROPOSER_SLASHINGS)),
          namedSchema(
              "attester_slashings",
              new SszListSchema<>(AttesterSlashing.TYPE, Constants.MAX_ATTESTER_SLASHINGS)),
          namedSchema(
              "attestations", new SszListSchema<>(Attestation.TYPE, Constants.MAX_ATTESTATIONS)),
          namedSchema("deposits", new SszListSchema<>(Deposit.TYPE, Constants.MAX_DEPOSITS)),
          namedSchema(
              "voluntary_exits",
              new SszListSchema<>(SignedVoluntaryExit.TYPE, Constants.MAX_VOLUNTARY_EXITS)));
    }

    public SszListSchema<ProposerSlashing> getProposerSlashingsType() {
      return (SszListSchema<ProposerSlashing>) getFieldSchema3();
    }

    public SszListSchema<AttesterSlashing> getAttesterSlashingsType() {
      return (SszListSchema<AttesterSlashing>) getFieldSchema4();
    }

    public SszListSchema<Attestation> getAttestationsType() {
      return (SszListSchema<Attestation>) getFieldSchema5();
    }

    public SszListSchema<Deposit> getDepositsType() {
      return (SszListSchema<Deposit>) getFieldSchema6();
    }

    public SszListSchema<SignedVoluntaryExit> getVoluntaryExitsType() {
      return (SszListSchema<SignedVoluntaryExit>) getFieldSchema7();
    }

    @Override
    public BeaconBlockBody createFromBackingNode(TreeNode node) {
      return new BeaconBlockBody(this, node);
    }
  }

  public static BeaconBlockBodyType getSszType() {
    return TYPE.get();
  }

  public static final SpecDependent<BeaconBlockBodyType> TYPE =
      SpecDependent.of(BeaconBlockBodyType::new);

  private BLSSignature randaoRevealCache;

  private BeaconBlockBody(BeaconBlockBodyType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated // Use the constructor with type
  public BeaconBlockBody(
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SSZList<ProposerSlashing> proposer_slashings,
      SSZList<AttesterSlashing> attester_slashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntary_exits) {
    this(
        TYPE.get(),
        randao_reveal,
        eth1_data,
        graffiti,
        proposer_slashings,
        attester_slashings,
        attestations,
        deposits,
        voluntary_exits);
    this.randaoRevealCache = randao_reveal;
  }

  public BeaconBlockBody(
      BeaconBlockBodyType type,
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      Bytes32 graffiti,
      SSZList<ProposerSlashing> proposer_slashings,
      SSZList<AttesterSlashing> attester_slashings,
      SSZList<Attestation> attestations,
      SSZList<Deposit> deposits,
      SSZList<SignedVoluntaryExit> voluntary_exits) {
    super(
        type,
        SszUtils.toSszByteVector(randao_reveal.toBytesCompressed()),
        eth1_data,
        new SszBytes32(graffiti),
        SszUtils.toSszList(type.getProposerSlashingsType(), proposer_slashings),
        SszUtils.toSszList(type.getAttesterSlashingsType(), attester_slashings),
        SszUtils.toSszList(type.getAttestationsType(), attestations),
        SszUtils.toSszList(type.getDepositsType(), deposits),
        SszUtils.toSszList(type.getVoluntaryExitsType(), voluntary_exits));
    this.randaoRevealCache = randao_reveal;
  }

  public BeaconBlockBody() {
    super(TYPE.get());
  }

  public BLSSignature getRandao_reveal() {
    if (randaoRevealCache == null) {
      randaoRevealCache = BLSSignature.fromBytesCompressed(SszUtils.getAllBytes(getField0()));
    }
    return randaoRevealCache;
  }

  public Eth1Data getEth1_data() {
    return getField1();
  }

  public Bytes32 getGraffiti() {
    return getField2().get();
  }

  public SSZList<ProposerSlashing> getProposer_slashings() {
    return new SSZBackingList<>(
        ProposerSlashing.class, getField3(), Function.identity(), Function.identity());
  }

  public SSZList<AttesterSlashing> getAttester_slashings() {
    return new SSZBackingList<>(
        AttesterSlashing.class, getField4(), Function.identity(), Function.identity());
  }

  public SSZList<Attestation> getAttestations() {
    return new SSZBackingList<>(
        Attestation.class, getField5(), Function.identity(), Function.identity());
  }

  public SSZList<Deposit> getDeposits() {
    return new SSZBackingList<>(
        Deposit.class, getField6(), Function.identity(), Function.identity());
  }

  public SSZList<SignedVoluntaryExit> getVoluntary_exits() {
    return new SSZBackingList<>(
        SignedVoluntaryExit.class, getField7(), Function.identity(), Function.identity());
  }
}
