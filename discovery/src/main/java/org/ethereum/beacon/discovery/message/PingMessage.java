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

package org.ethereum.beacon.discovery.message;

import com.google.common.base.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

// import tech.pegasys.artemis.util.bytes.Bytes1;
// import tech.pegasys.artemis.util.bytes.Bytes;
// import tech.pegasys.artemis.util.uint.UInt64;

/**
 * PING checks whether the recipient is alive and informs it about the sender's ENR sequence number.
 */
public class PingMessage implements V5Message {
  // Unique request id
  private final Bytes requestId;
  // Local ENR sequence number of sender
  private final UInt64 enrSeq;

  public PingMessage(Bytes requestId, UInt64 enrSeq) {
    this.requestId = requestId;
    this.enrSeq = enrSeq;
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public UInt64 getEnrSeq() {
    return enrSeq;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(MessageCode.PING.byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(
                    RlpString.create(requestId.toArray()),
                    RlpString.create(enrSeq.toBigInteger())))));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PingMessage that = (PingMessage) o;
    return Objects.equal(requestId, that.requestId) && Objects.equal(enrSeq, that.enrSeq);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(requestId, enrSeq);
  }

  @Override
  public String toString() {
    return "PingMessage{" + "requestId=" + requestId + ", enrSeq=" + enrSeq + '}';
  }
}
