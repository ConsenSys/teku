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
import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import tech.pegasys.teku.storage.server.blob.BlobStorage;
import tech.pegasys.teku.storage.server.blob.BlobStorage.BlobTransaction;

public class SqlChainStorage extends AbstractSqlStorage {
  private static final Logger LOG = LogManager.getLogger();

  private final BlobStorage blobStorage;

  public SqlChainStorage(
      PlatformTransactionManager transactionManager,
      final ComboPooledDataSource dataSource,
      final BlobStorage blobStorage) {
    super(transactionManager, dataSource);
    this.blobStorage = blobStorage;
  }

  @MustBeClosed
  public SqlChainStorage.Transaction startTransaction() {
    return new Transaction(
        transactionManager.getTransaction(TransactionDefinition.withDefaults()),
        transactionManager,
        jdbc,
        blobStorage.startTransaction());
  }

  public Optional<SignedBeaconBlock> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return getBlockByBlockRoot(blockRoot, false);
  }

  public Optional<SignedBeaconBlock> getHotBlockByBlockRoot(final Bytes32 blockRoot) {
    return getBlockByBlockRoot(blockRoot, true);
  }

  public Optional<SignedBeaconBlock> getBlockByBlockRoot(
      final Bytes32 blockRoot, final boolean onlyNonFinalized) {
    String sql = "SELECT blobId FROM block WHERE blockRoot = ?";
    if (onlyNonFinalized) {
      sql += " AND finalized == 0";
    }
    return loadSingle(sql, (rs, rowNum) -> getBlob(rs, SignedBeaconBlock.class), blockRoot);
  }

  public Optional<SignedBeaconBlock> getFinalizedBlockBySlot(final UInt64 slot) {
    return loadSingle(
        "SELECT blobId FROM block WHERE slot = ? AND finalized = true",
        (rs, rowNum) -> getBlob(rs, SignedBeaconBlock.class),
        slot.bigIntegerValue());
  }

  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return loadSingle(
        "SELECT blobId FROM block WHERE slot <= ? AND finalized = true ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getBlob(rs, SignedBeaconBlock.class),
        slot.bigIntegerValue());
  }

  public Map<Bytes32, Bytes32> getHotBlockChildToParentLookup() {
    return loadMap(
        "SELECT blockRoot, parentRoot FROM block WHERE finalized = false",
        rs -> getBytes32(rs, "blockRoot"),
        rs -> getBytes32(rs, "parentRoot"));
  }

  public Map<Bytes32, UInt64> getBlockRootToSlotLookup() {
    return loadMap(
        "SELECT blockRoot, slot FROM block WHERE finalized = false",
        rs -> getBytes32(rs, "blockRoot"),
        rs -> getUInt64(rs, "slot"));
  }

  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return SqlStream.stream(
        jdbc,
        20,
        "SELECT blobId FROM block WHERE finalized = true AND slot >= ? AND slot <= ? ORDER BY slot",
        (rs, rowNum) -> getBlob(rs, SignedBeaconBlock.class),
        startSlot.bigIntegerValue(),
        endSlot.bigIntegerValue());
  }

  public Optional<BeaconState> getStateByBlockRoot(final Bytes32 blockRoot) {
    return loadSingle(
        "SELECT blobId FROM state WHERE blockRoot = ? ORDER BY slot LIMIT 1",
        (rs, rowNum) -> getBlob(rs, BeaconStateImpl.class),
        blockRoot);
  }

  public Optional<BeaconState> getHotStateByBlockRoot(final Bytes32 blockRoot) {
    return loadSingle(
        "         SELECT s.blobId "
            + "     FROM state s "
            + "     JOIN block b ON b.blockRoot = s.blockRoot "
            + "    WHERE s.blockRoot = ? "
            + "      AND b.finalized = 0 "
            + " ORDER BY s.slot LIMIT 1",
        (rs, rowNum) -> getBlob(rs, BeaconStateImpl.class),
        blockRoot);
  }

  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return loadSingle(
        "        SELECT s.blobId FROM state s "
            + "    JOIN block b ON s.blockRoot = b.blockRoot"
            + "   WHERE s.slot <= ? "
            + "     AND s.blobId IS NOT NULL "
            + "     AND b.finalized "
            + "ORDER BY s.slot DESC "
            + "   LIMIT 1",
        (rs, rowNum) -> getBlob(rs, BeaconStateImpl.class),
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

  protected <T> T getBlob(final ResultSet rs, final Class<? extends T> type) throws SQLException {
    final Bytes32 blobId = getBytes32(rs, "blobId");
    if (blobId == null) {
      return null;
    }
    return blobStorage
        .load(blobId)
        .map(data -> SimpleOffsetSerializer.deserialize(data, type))
        .orElse(null);
  }

  @Override
  public void close() {
    super.close();
    blobStorage.close();
  }

  public static class Transaction extends AbstractSqlTransaction {

    private final BlobTransaction blobStorageTransaction;

    protected Transaction(
        final TransactionStatus transaction,
        final PlatformTransactionManager transactionManager,
        final JdbcOperations jdbc,
        final BlobTransaction blobStorageTransaction) {
      super(transaction, transactionManager, jdbc);
      this.blobStorageTransaction = blobStorageTransaction;
    }

    public void storeBlock(final SignedBeaconBlock block, final boolean finalized) {
      storeBlocks(List.of(block), finalized);
    }

    public void storeBlocks(final Collection<SignedBeaconBlock> blocks, final boolean finalized) {
      batchUpdate(
          "INSERT INTO block (blockRoot, slot, parentRoot, finalized, blobId) VALUES (?, ?, ?, ?, ?) "
              + " ON CONFLICT(blockRoot) DO UPDATE SET finalized = IIF(excluded.finalized, TRUE, finalized)",
          blocks.stream()
              .map(
                  block -> {
                    final Bytes32 blockRoot = block.getRoot();
                    return new Object[] {
                      blockRoot,
                      block.getSlot(),
                      block.getParent_root(),
                      finalized,
                      storeBlob(blockRoot, block)
                    };
                  }));
    }

    public void finalizeBlocks(final Collection<Bytes32> blockRoots) {
      batchUpdate("UPDATE block SET finalized = true WHERE blockRoot = ?", blockRoots);
    }

    public void deleteHotBlockByBlockRoot(final Bytes32 blockRoot) {
      final int deletedRows =
          execSql("DELETE FROM block WHERE blockRoot = ? AND NOT finalized", blockRoot);
      if (deletedRows > 0) {
        blobStorageTransaction.delete(blockRoot);
        deleteStatesByBlockRoot(blockRoot);
      }
    }

    private void deleteStatesByBlockRoot(final Bytes32 blockRoot) {
      loadForEach(
          "SELECT stateRoot FROM state WHERE blockRoot = ?",
          rs -> blobStorageTransaction.delete(getBytes32(rs, "stateRoot")),
          blockRoot);
      execSql("DELETE FROM state WHERE blockRoot = ?", blockRoot);
    }

    public void storeStateRoots(final Map<Bytes32, SlotAndBlockRoot> stateRoots) {
      batchUpdate(
          "INSERT INTO state (stateRoot, blockRoot, slot) VALUES (?, ?, ?)"
              + " ON CONFLICT(stateRoot) DO NOTHING",
          stateRoots.entrySet().stream()
              .map(
                  entry ->
                      new Object[] {
                        entry.getKey(), entry.getValue().getBlockRoot(), entry.getValue().getSlot()
                      }));
    }

    public void storeStates(final Map<Bytes32, BeaconState> states) {
      batchUpdate(
          "INSERT INTO state (stateRoot, blockRoot, slot, blobId) VALUES (?, ?, ?, ?)"
              + " ON CONFLICT(stateRoot) DO UPDATE SET blobId = excluded.blobId",
          states.entrySet().stream()
              .map(
                  entry -> {
                    final Bytes32 blockRoot = entry.getKey();
                    final BeaconState state = entry.getValue();
                    final Bytes32 stateRoot = state.hash_tree_root();
                    return new Object[] {
                      stateRoot, blockRoot, state.getSlot(), storeBlob(stateRoot, state)
                    };
                  }));
    }

    /** Deletes any states prior to the most recent finalized state. */
    public void pruneFinalizedStates() {
      final Optional<UInt64> latestFinalizedStateSlot =
          loadSingle(
              "     SELECT MAX(s.slot) AS latestFinalizedStateSlot"
                  + " FROM state s "
                  + " JOIN block b ON s.blockRoot = b.blockRoot "
                  + "WHERE b.finalized "
                  + "  AND s.blobId IS NOT NULL",
              (rs, rowNum) -> getUInt64(rs, "latestFinalizedStateSlot"));
      if (latestFinalizedStateSlot.isEmpty()) {
        LOG.debug("Skipping pruning as no latest finalized state found.");
        return;
      }
      loadForEach(
          "SELECT stateRoot FROM state WHERE slot < ?",
          rs -> blobStorageTransaction.delete(getBytes32(rs, "stateRoot")),
          latestFinalizedStateSlot.get());
      execSql(" DELETE FROM state " + " WHERE slot < ?", latestFinalizedStateSlot.get());
    }

    /**
     * Deletes the stored data for states to reduce the number of retained states to match the
     * specified state storage frequency.
     */
    public void trimFinalizedStates(
        final UInt64 afterSlot,
        final UInt64 latestFinalizedSlot,
        final UInt64 stateStorageFrequency) {
      final NavigableMap<UInt64, Bytes32> availableStateRootsBySlot =
          loadMap(
              new TreeMap<>(),
              "     SELECT slot, stateRoot "
                  + " FROM state "
                  + "WHERE blobId IS NOT NULL "
                  + "  AND slot > ? "
                  + "  AND slot <= ?",
              rs -> getUInt64(rs, "slot"),
              rs -> getBytes32(rs, "stateRoot"),
              afterSlot,
              latestFinalizedSlot);
      if (availableStateRootsBySlot.isEmpty()) {
        return;
      }
      final Optional<UInt64> priorAvailableSlot =
          loadSingle(
              "     SELECT MAX(slot) AS priorAvailableSlot "
                  + " FROM state "
                  + "WHERE blobId IS NOT NULL "
                  + "  AND slot <= ?",
              (rs, rowNum) -> getUInt64(rs, "priorAvailableSlot"),
              afterSlot);
      final Set<Bytes32> rootsToDelete = new HashSet<>();
      UInt64 lastAvailableSlot =
          priorAvailableSlot.orElseGet(() -> availableStateRootsBySlot.pollFirstEntry().getKey());
      while (!availableStateRootsBySlot.isEmpty()) {
        final NavigableMap<UInt64, Bytes32> statesWithinFrequency =
            availableStateRootsBySlot.headMap(lastAvailableSlot.plus(stateStorageFrequency), false);
        rootsToDelete.addAll(statesWithinFrequency.values());
        statesWithinFrequency.clear();
        if (availableStateRootsBySlot.isEmpty()) {
          break;
        }
        lastAvailableSlot = availableStateRootsBySlot.pollFirstEntry().getKey();
      }

      rootsToDelete.forEach(blobStorageTransaction::delete);
      batchUpdate("DELETE FROM state WHERE stateRoot = ?", rootsToDelete);
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
      batchUpdate(
          "INSERT INTO vote (validatorIndex, currentRoot, nextRoot, nextEpoch) VALUES (?, ?, ?, ?)"
              + " ON CONFLICT(validatorIndex) DO UPDATE SET"
              + "                     currentRoot = excluded.currentRoot,"
              + "                     nextRoot = excluded.nextRoot,"
              + "                     nextEpoch = excluded.nextEpoch",
          votes.entrySet().stream()
              .map(
                  entry ->
                      new Object[] {
                        entry.getKey(),
                        entry.getValue().getCurrentRoot(),
                        entry.getValue().getNextRoot(),
                        entry.getValue().getNextEpoch()
                      }));
    }

    @Override
    protected void doCommit() {
      blobStorageTransaction.commit();
      super.doCommit();
    }

    @Override
    protected void doRollback() {
      blobStorageTransaction.close();
      super.doRollback();
    }

    private Bytes32 storeBlob(final Bytes32 id, final SimpleOffsetSerializable obj) {
      return blobStorageTransaction.store(id, obj);
    }
  }
}
