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

package tech.pegasys.teku.core.results;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.data.BlockProcessingRecord;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;

import java.util.Optional;

public class SuccessfulBlockImportResult implements BlockImportResult {

  private final SignedBeaconBlock block;
  private final Optional<BlockProcessingRecord> record;
  private boolean blockOnCanonicalChain = false;

  public SuccessfulBlockImportResult(
      final SignedBeaconBlock block, final Optional<BlockProcessingRecord> record) {
    this.block = block;
    this.record = record;
  }

  public void setBlockOnCanonicalChain(boolean blockOnCanonicalChain) {
    this.blockOnCanonicalChain = blockOnCanonicalChain;
  }

  @Override
  public boolean isBlockOnCanonicalChain() {
    return blockOnCanonicalChain;
  }

  @Override
  public boolean isSuccessful() {
    return true;
  }

  @Override
  public Optional<BlockProcessingRecord> getBlockProcessingRecord() {
    return record;
  }

  @Override
  public SignedBeaconBlock getBlock() {
    return block;
  }

  @Override
  public FailureReason getFailureReason() {
    return null;
  }

  @Override
  public Optional<Throwable> getFailureCause() {
    return Optional.empty();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("block", block).toString();
  }
}
