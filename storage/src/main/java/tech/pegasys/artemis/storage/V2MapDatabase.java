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

package tech.pegasys.artemis.storage;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.Atomic;
import org.mapdb.Atomic.Var;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DBMaker.Maker;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.storage.utils.Bytes32Serializer;
import tech.pegasys.artemis.storage.utils.MapDBSerializer;
import tech.pegasys.artemis.storage.utils.UnsignedLongSerializer;

public class V2MapDatabase implements Database {
  private static final Logger LOG = LogManager.getLogger();
  private final DB db;
  private final Var<UnsignedLong> time;
  private final Var<UnsignedLong> genesisTime;
  private final Atomic.Var<Checkpoint> justifiedCheckpoint;
  private final Atomic.Var<Checkpoint> bestJustifiedCheckpoint;
  private final Atomic.Var<Checkpoint> finalizedCheckpoint;

  //  private final ConcurrentNavigableMap<UnsignedLong, Bytes32> finalizedRootsBySlot;
  //  private final ConcurrentMap<Bytes32, BeaconBlock> finalizedBlocksByRoot;
  //  private final ConcurrentMap<Bytes32, BeaconState> finalizedStatesByRoot;
  private final ConcurrentMap<Bytes32, BeaconBlock> hotBlocksByRoot;
  private final ConcurrentMap<Bytes32, BeaconState> hotStatesByRoot;

  private final ConcurrentMap<Checkpoint, BeaconState> checkpointStates;
  private final ConcurrentMap<UnsignedLong, Checkpoint> latestMessages;

  // In memory only
  private final ConcurrentNavigableMap<UnsignedLong, Set<Bytes32>> hotRootsBySlotCache =
      new ConcurrentSkipListMap<>();

  public static Database createOnDisk(final File directory) {
    // TODO: Record version and db type to a file.
    return new V2MapDatabase(DBMaker.fileDB(new File(directory, "artemis.db")));
  }

  public static Database createInMemory() {
    return new V2MapDatabase(DBMaker.memoryDB());
  }

  private V2MapDatabase(final Maker dbMaker) {
    db = dbMaker.transactionEnable().make();
    time = db.atomicVar("time", new UnsignedLongSerializer()).createOrOpen();
    genesisTime = db.atomicVar("genesisTime", new UnsignedLongSerializer()).createOrOpen();
    justifiedCheckpoint =
        db.atomicVar("justifiedCheckpoint", new MapDBSerializer<>(Checkpoint.class)).createOrOpen();
    bestJustifiedCheckpoint =
        db.atomicVar("bestJustifiedCheckpoint", new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();
    finalizedCheckpoint =
        db.atomicVar("finalizedCheckpoint", new MapDBSerializer<>(Checkpoint.class)).createOrOpen();

    //    finalizedRootsBySlot =
    //        db.treeMap("finalizedRootsBySlot", new UnsignedLongSerializer(), new
    // Bytes32Serializer())
    //            .createOrOpen();
    //    finalizedBlocksByRoot =
    //        db.hashMap(
    //                "finalizedBlocksByRoot",
    //                new Bytes32Serializer(),
    //                new MapDBSerializer<>(BeaconBlock.class))
    //            .createOrOpen();
    //    finalizedStatesByRoot =
    //        db.hashMap(
    //                "finalizedStatsByRoot",
    //                new Bytes32Serializer(),
    //                new MapDBSerializer<>(BeaconState.class))
    //            .createOrOpen();

    hotBlocksByRoot =
        db.hashMap(
                "hotBlocksByRoot",
                new Bytes32Serializer(),
                new MapDBSerializer<>(BeaconBlock.class))
            .createOrOpen();
    hotStatesByRoot =
        db.hashMap(
                "hotStatesByRoot",
                new Bytes32Serializer(),
                new MapDBSerializer<>(BeaconState.class))
            .createOrOpen();

    checkpointStates =
        db.hashMap(
                "checkpointStates",
                new MapDBSerializer<>(Checkpoint.class),
                new MapDBSerializer<>(BeaconState.class))
            .createOrOpen();

    latestMessages =
        db.hashMap(
                "latestMessages",
                new UnsignedLongSerializer(),
                new MapDBSerializer<>(Checkpoint.class))
            .createOrOpen();

    // Recreate hotRootsBySlotCache
    hotBlocksByRoot.forEach(this::addToHotRootsBySlotCache);
  }

  @Override
  public void insert(final Transaction transaction) {
    final Checkpoint previousFinalizedCheckpoint = finalizedCheckpoint.get();
    final Checkpoint newFinalizedCheckpoint = transaction.getFinalizedCheckpoint();
    time.set(transaction.getTime());
    genesisTime.set(transaction.getGenesisTime());
    this.finalizedCheckpoint.set(newFinalizedCheckpoint);
    justifiedCheckpoint.set(transaction.getJustifiedCheckpoint());
    bestJustifiedCheckpoint.set(transaction.getBestJustifiedCheckpoint());
    checkpointStates.putAll(transaction.getCheckpointStates());
    latestMessages.putAll(transaction.getLatestMessages());

    transaction
        .getBlocks()
        .forEach(
            (root, block) -> {
              hotBlocksByRoot.put(root, block);
              addToHotRootsBySlotCache(root, block);
            });
    hotStatesByRoot.putAll(transaction.getBlockStates());

    if (previousFinalizedCheckpoint == null
        || !previousFinalizedCheckpoint.equals(newFinalizedCheckpoint)) {
      checkpointStates
          .keySet()
          .removeIf(
              checkpoint -> checkpoint.getEpoch().compareTo(newFinalizedCheckpoint.getEpoch()) < 0);

      // TODO: Should we be finalizing until the end of the epoch (and is epoch 0 special in that
      // case)?
      final UnsignedLong startOfFinalizedEpoch =
          compute_start_slot_at_epoch(newFinalizedCheckpoint.getEpoch());
      LOG.debug("Removing non-finalized blocks prior to {}", startOfFinalizedEpoch);
      hotRootsBySlotCache
          .headMap(startOfFinalizedEpoch)
          .values()
          .forEach(
              roots -> {
                hotBlocksByRoot.keySet().removeAll(roots);
                hotStatesByRoot.keySet().removeAll(roots);
              });
    }
    db.commit();
  }

  private void addToHotRootsBySlotCache(final Bytes32 root, final BeaconBlock block) {
    hotRootsBySlotCache
        .computeIfAbsent(
            block.getSlot(), key -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
        .add(root);
  }

  @Override
  public Store createMemoryStore() {
    return new Store(
        time.get(),
        genesisTime.get(),
        justifiedCheckpoint.get(),
        finalizedCheckpoint.get(),
        bestJustifiedCheckpoint.get(),
        hotBlocksByRoot,
        hotStatesByRoot,
        checkpointStates,
        latestMessages);
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    return Optional.empty();
  }

  @Override
  public Optional<BeaconBlock> getBlock(final Bytes32 root) {
    return Optional.ofNullable(hotBlocksByRoot.get(root));
  }

  @Override
  public Optional<BeaconState> getState(final Bytes32 root) {
    return Optional.ofNullable(hotStatesByRoot.get(root));
  }

  @Override
  public void close() {
    db.close();
  }
}
