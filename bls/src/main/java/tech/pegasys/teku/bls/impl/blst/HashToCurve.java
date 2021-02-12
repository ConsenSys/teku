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

package tech.pegasys.teku.bls.impl.blst;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.blst.swig.P2;

class HashToCurve {
  // The ciphersuite defined in the Eth2 specification which also serves as domain separation tag
  // https://github.com/ethereum/eth2.0-specs/blob/v0.12.0/specs/phase0/beacon-chain.md#bls-signatures
  static final String ETH2_DST = "BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_POP_";

  static P2 hashToG2(Bytes message) {
    return hashToG2(message, ETH2_DST);
  }

  static P2 hashToG2(Bytes message, String dst) {
    P2 p2Hash = new P2();
    try {
      return p2Hash.hash_to(message.toArray(), dst, new byte[0]);
    } catch (Exception e) {
      p2Hash.delete();
      throw e;
    }
  }
}
