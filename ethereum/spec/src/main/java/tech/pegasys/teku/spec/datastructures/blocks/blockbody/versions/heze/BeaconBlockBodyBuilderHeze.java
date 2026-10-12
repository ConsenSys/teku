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

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyBuilderGloas;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

/** Builds Heze block bodies, which have no {@code eth1_data} or {@code deposits} (EIP-8015). */
public class BeaconBlockBodyBuilderHeze extends BeaconBlockBodyBuilderGloas {

  public BeaconBlockBodyBuilderHeze(final BeaconBlockBodySchemaHezeImpl schema) {
    super(schema);
  }

  @Override
  protected void validate() {
    checkNotNull(randaoReveal, "randaoReveal must be specified");
    checkNotNull(graffiti, "graffiti must be specified");
    checkNotNull(attestations, "attestations must be specified");
    checkNotNull(proposerSlashings, "proposerSlashings must be specified");
    checkNotNull(attesterSlashings, "attesterSlashings must be specified");
    checkNotNull(voluntaryExits, "voluntaryExits must be specified");
    checkNotNull(syncAggregate, "syncAggregate must be specified");
    checkNotNull(blsToExecutionChanges, "blsToExecutionChanges must be specified");
    checkNotNull(signedExecutionPayloadBid, "signedExecutionPayloadBid must be specified");
    checkNotNull(payloadAttestations, "payloadAttestations must be specified");
    checkNotNull(parentExecutionRequests, "parentExecutionRequests must be specified");
  }

  @Override
  public BeaconBlockBody build() {
    validate();
    final BeaconBlockBodySchemaHezeImpl schema =
        getAndValidateSchema(false, BeaconBlockBodySchemaHezeImpl.class);
    return new BeaconBlockBodyHezeImpl(
        schema,
        new SszSignature(randaoReveal),
        SszBytes32.of(graffiti),
        proposerSlashings,
        attesterSlashings,
        attestations,
        voluntaryExits,
        syncAggregate,
        getBlsToExecutionChanges(),
        signedExecutionPayloadBid,
        payloadAttestations,
        parentExecutionRequests);
  }
}
