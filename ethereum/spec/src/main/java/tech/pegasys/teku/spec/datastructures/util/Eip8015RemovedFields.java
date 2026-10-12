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

package tech.pegasys.teku.spec.datastructures.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;

/**
 * Heze removes the Eth1 deposit bridge and Eth1 data voting fields (EIP-8015). Heze logic should
 * never access them, so the accessors kept on the shared interfaces return empty values and log an
 * error, with a stack trace to find the caller.
 */
public final class Eip8015RemovedFields {

  private static final Logger LOG = LogManager.getLogger();

  public static final Eth1Data EMPTY_ETH1_DATA = new Eth1Data();
  public static final UInt64 EMPTY_INDEX = UInt64.ZERO;
  // Writes to a mutable copy are dropped, so allow appends rather than failing them
  public static final SszList<Eth1Data> EMPTY_ETH1_DATA_VOTES =
      SszProgressiveListSchema.create(Eth1Data.SSZ_SCHEMA, Integer.MAX_VALUE).getDefault();
  public static final SszList<Deposit> EMPTY_DEPOSITS =
      SszProgressiveListSchema.create(Deposit.SSZ_SCHEMA, 0).getDefault();

  private Eip8015RemovedFields() {}

  public static void logAccess(final SszFieldName field) {
    final String fieldName = field.getSszFieldName();
    LOG.error(
        "Accessed {}, which was removed in Heze (EIP-8015)",
        fieldName,
        new IllegalStateException("Removed field " + fieldName + " accessed"));
  }
}
