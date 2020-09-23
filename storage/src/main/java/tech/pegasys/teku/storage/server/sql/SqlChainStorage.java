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

package tech.pegasys.teku.storage.server.sql;

import com.google.errorprone.annotations.MustBeClosed;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class SqlChainStorage extends AbstractSqlStorage {

  public SqlChainStorage(
      PlatformTransactionManager transactionManager, final HikariDataSource dataSource) {
    super(transactionManager, dataSource);
  }

  @MustBeClosed
  public SqlChainStorage.Transaction startTransaction() {
    return new Transaction(
        transactionManager.getTransaction(TransactionDefinition.withDefaults()),
        transactionManager,
        jdbc);
  }

  public Optional<SignedBeaconBlock> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return getBlockByBlockRoot(blockRoot, false);
  }

  public Optional<SignedBeaconBlock> getHotBlockByBlockRoot(final Bytes32 blockRoot) {
    return getBlockByBlockRoot(blockRoot, true);
  }

  public Optional<SignedBeaconBlock> getBlockByBlockRoot(
      final Bytes32 blockRoot, final boolean onlyNonFinalized) {
    String sql = "SELECT ssz FROM block WHERE blockRoot = ?";
    if (onlyNonFinalized) {
      sql += " AND finalized == 0";
    }
    return loadSingle(sql, (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class), blockRoot);
  }

  public Optional<SignedBeaconBlock> getFinalizedBlockBySlot(final UInt64 slot) {
    return loadSingle(
        "SELECT ssz FROM block WHERE slot = ? AND finalized = true",
        (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class),
        slot.bigIntegerValue());
  }

  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return loadSingle(
        "SELECT ssz FROM block WHERE slot <= ? AND finalized = true ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class),
        slot.bigIntegerValue());
  }

  public Optional<BeaconState> getStateByBlockRoot(final Bytes32 blockRoot) {
    return getStateByBlockRoot(blockRoot, false);
  }

  public Optional<BeaconState> getHotStateByBlockRoot(final Bytes32 blockRoot) {
    return getStateByBlockRoot(blockRoot, true);
  }

  private Optional<BeaconState> getStateByBlockRoot(
      final Bytes32 blockRoot, final boolean onlyNonfinalized) {
    final String sql;
    if (onlyNonfinalized) {
      sql =
          "   SELECT s.ssz FROM state s "
              + "     JOIN block b ON b.blockRoot = s.blockRoot "
              + "    WHERE s.blockRoot = ? "
              + "      AND b.finalized = 0 "
              + " ORDER BY s.slot LIMIT 1";
    } else {
      sql = "SELECT ssz FROM state WHERE blockRoot = ? ORDER BY slot LIMIT 1";
    }
    return loadSingle(sql, (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class), blockRoot);
  }

  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return loadSingle(
        "SELECT ssz FROM state WHERE slot <= ? AND ssz IS NOT NULL ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class),
        maxSlot);
  }

  public Optional<SlotAndBlockRoot> getSlotAndBlockRootByStateRoot(final Bytes32 stateRoot) {
    return loadSingle(
        "SELECT blockRoot, slot FROM state WHERE stateRoot = ?",
        (rs, rowNum) -> new SlotAndBlockRoot(getUInt64(rs, "slot"), getBytes32(rs, "blockRoot")),
        stateRoot);
  }

  public Optional<Checkpoint> getCheckpoint(final CheckpointType type) {
    return loadSingle(
        "SELECT * FROM checkpoint WHERE type = ?",
        (rs, rowNum) -> new Checkpoint(getUInt64(rs, "epoch"), getBytes32(rs, "blockRoot")),
        type);
  }

  public Map<UInt64, VoteTracker> getVotes() {
    final Map<UInt64, VoteTracker> votes = new HashMap<>();
    loadForEach(
        "SELECT validatorIndex, currentRoot, nextRoot, nextEpoch from vote",
        rs ->
            votes.put(
                getUInt64(rs, "validatorIndex"),
                new VoteTracker(
                    getBytes32(rs, "currentRoot"),
                    getBytes32(rs, "nextRoot"),
                    getUInt64(rs, "nextEpoch"))));
    return votes;
  }

  public static class Transaction extends AbstractSqlTransaction {

    protected Transaction(
        final TransactionStatus transaction,
        final PlatformTransactionManager transactionManager,
        final JdbcOperations jdbc) {
      super(transaction, transactionManager, jdbc);
    }

    public void storeBlock(final SignedBeaconBlock block, final boolean finalized) {
      execSql(
          "INSERT INTO block (blockRoot, slot, parentRoot, finalized, ssz) VALUES (?, ?, ?, ?, ?) "
              + " ON CONFLICT(blockRoot) DO UPDATE SET finalized = excluded.finalized",
          block.getRoot(),
          block.getSlot(),
          block.getParent_root(),
          finalized,
          serializeSsz(block));
    }

    public void finalizeBlock(final SignedBeaconBlock block) {
      execSql("UPDATE block SET finalized = true WHERE blockRoot = ?", block.getRoot());
    }

    public void deleteHotBlockByBlockRoot(final Bytes32 blockRoot) {
      execSql("DELETE FROM block WHERE blockRoot = ? AND NOT finalized", blockRoot);
    }

    public void storeStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
      execSql(
          "INSERT INTO state (stateRoot, blockRoot, slot) VALUES (?, ?, ?)"
              + " ON CONFLICT(stateRoot) DO NOTHING",
          stateRoot,
          slotAndBlockRoot.getBlockRoot(),
          slotAndBlockRoot.getSlot());
    }

    public void storeState(final Bytes32 blockRoot, final BeaconState state) {
      execSql(
          "INSERT INTO state (stateRoot, blockRoot, slot, ssz) VALUES (?, ?, ?, ?)"
              + " ON CONFLICT(stateRoot) DO UPDATE SET ssz = excluded.ssz",
          state.hash_tree_root(),
          blockRoot,
          state.getSlot(),
          serializeSsz(state));
    }

    public void deleteStateByBlockRoot(final Bytes32 blockRoot) {
      execSql("DELETE FROM state WHERE blockRoot = ?", blockRoot);
    }

    public void pruneFinalizedStates() {
      execSql(
          " DELETE FROM state "
              + " WHERE slot < "
              + "    (SELECT MAX(s2.slot) "
              + "      FROM state s2 "
              + "      JOIN block b ON s2.blockRoot = b.blockRoot "
              + "     WHERE b.finalized"
              + "       AND s2.ssz IS NOT NULL)");
    }

    public void storeCheckpoint(final CheckpointType type, final Checkpoint checkpoint) {
      execSql(
          "INSERT INTO checkpoint (type, blockRoot, epoch) VALUES (?, ?, ?)"
              + " ON CONFLICT(type) DO UPDATE SET blockRoot = excluded.blockRoot, epoch = excluded.epoch",
          type,
          checkpoint.getRoot(),
          checkpoint.getEpoch());
    }

    public void clearCheckpoint(final CheckpointType type) {
      execSql("DELETE FROM checkpoint WHERE type = ?", type);
    }

    public void storeVotes(final Map<UInt64, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, voteTracker) ->
              execSql(
                  "INSERT INTO vote (validatorIndex, currentRoot, nextRoot, nextEpoch) VALUES (?, ?, ?, ?)"
                      + " ON CONFLICT(validatorIndex) DO UPDATE SET"
                      + "                     currentRoot = excluded.currentRoot,"
                      + "                     nextRoot = excluded.nextRoot,"
                      + "                     nextEpoch = excluded.nextEpoch",
                  validatorIndex,
                  voteTracker.getCurrentRoot(),
                  voteTracker.getNextRoot(),
                  voteTracker.getNextEpoch()));
    }

    private Bytes serializeSsz(final SimpleOffsetSerializable obj) {
      return SimpleOffsetSerializer.serialize(obj);
    }
  }
}
