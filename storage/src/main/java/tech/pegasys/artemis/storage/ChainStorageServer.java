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

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_of_epoch;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.mapdb.Atomic;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.events.DBStoreValidEvent;
import tech.pegasys.artemis.storage.events.NewAttestationEvent;
import tech.pegasys.artemis.storage.events.NodeStartEvent;
import tech.pegasys.artemis.storage.events.ProcessedBlockEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.storage.serializers.Bytes32Serializer;
import tech.pegasys.artemis.storage.serializers.LatestMessageSerializer;
import tech.pegasys.artemis.storage.serializers.SSZSerializer;
import tech.pegasys.artemis.storage.serializers.UnsignedLongSerializer;
import tech.pegasys.artemis.util.alogger.ALogger;

/** This class is the ChainStorage server-side logic */
public class ChainStorageServer implements ChainStorage {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final EventBus eventBus;
  private static DB db = DBMaker.fileDB("artemis.db").transactionEnable().make();

  // Store
  private Atomic.Var<UnsignedLong> time;
  private Atomic.Var<Checkpoint> justifiedCheckpoint;
  private Atomic.Var<Checkpoint> finalizedCheckpoint;
  private ConcurrentMap<Bytes32, BeaconBlock> blocks;
  private ConcurrentMap<Bytes32, BeaconState> block_states;
  private ConcurrentMap<Checkpoint, BeaconState> checkpoint_states;
  private ConcurrentMap<UnsignedLong, LatestMessage> latest_messages;

  public ChainStorageServer(EventBus eventBus) {
    this.eventBus = eventBus;
    // Store initialization
    time = db.atomicVar("time", new UnsignedLongSerializer()).createOrOpen();
    justifiedCheckpoint =
        db.atomicVar("justified_checkpoint", new SSZSerializer<>(Checkpoint.class)).createOrOpen();
    finalizedCheckpoint =
        db.atomicVar("finalized_checkpoint", new SSZSerializer<>(Checkpoint.class)).createOrOpen();
    blocks =
        db.hashMap("blocks_map", new Bytes32Serializer(), new SSZSerializer<>(BeaconBlock.class))
            .createOrOpen();
    block_states =
        db.hashMap(
                "block_states_map", new Bytes32Serializer(), new SSZSerializer<>(BeaconState.class))
            .createOrOpen();
    checkpoint_states =
        db.hashMap(
                "checkpoint_states_map",
                new SSZSerializer<>(Checkpoint.class),
                new SSZSerializer<>(BeaconState.class))
            .createOrOpen();
    latest_messages =
        db.hashMap(
                "latest_messages_map", new UnsignedLongSerializer(), new LatestMessageSerializer())
            .createOrOpen();

    this.eventBus.register(this);
    STDOUT.log(Level.INFO, "Storage server constructed");
  }

  private boolean checkIfStorageServerInitialized(UnsignedLong genesisTime) {
    UnsignedLong latestDBtime = time.get();
    STDOUT.log(Level.INFO, "Latest time in DB: " + latestDBtime);
    return !(latestDBtime == null || genesisTime.compareTo(latestDBtime) >= 0);
  }

  private Store getStoreFromDB() {
    return new Store(
        time.get(),
        justifiedCheckpoint.get(),
        finalizedCheckpoint.get(),
        new ConcurrentHashMap<Bytes32, BeaconBlock>(blocks),
        new ConcurrentHashMap<Bytes32, BeaconState>(block_states),
        new ConcurrentHashMap<Checkpoint, BeaconState>(checkpoint_states));
  }

  @Subscribe
  public void onNodeStart(NodeStartEvent nodeStartEvent) {
    STDOUT.log(Level.INFO, "At node start event");
    UnsignedLong genesisTime = nodeStartEvent.getState().getGenesis_time();
    if (checkIfStorageServerInitialized(genesisTime)) {
      STDOUT.log(Level.INFO, "Restoring state from database");

      // TODO: Confirm this is the right way to work out the node slot.
      final Store store = getStoreFromDB();
      final UnsignedLong nodeSlot = store.getTime()
          .minus(genesisTime)
          .dividedBy(UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT));
      this.eventBus.post(new DBStoreValidEvent(store, nodeSlot));
    }
  }

  @Subscribe
  public void onNewProcessedBlock(ProcessedBlockEvent processedBlockEvent) {
    BeaconStateWithCache postState = processedBlockEvent.getPostState();
    BeaconBlock processedBlock = processedBlockEvent.getProcessedBlock();
    Checkpoint newJustifiedCheckpoint = processedBlockEvent.getJustifiedCheckpoint();
    Checkpoint newFinalizedCheckpoint = processedBlockEvent.getFinalizedCheckpoint();

    Bytes32 blockRoot = processedBlock.signing_root("signature");
    block_states.put(blockRoot, postState);
    blocks.put(blockRoot, processedBlock);
    justifiedCheckpoint.set(newJustifiedCheckpoint);
    finalizedCheckpoint.set(newFinalizedCheckpoint);
    db.commit();
  }

  @Subscribe
  public void onNewAttestation(NewAttestationEvent newAttestationEvent) {
    Checkpoint checkpoint = newAttestationEvent.getCheckpoint();
    BeaconStateWithCache state = newAttestationEvent.getState();
    List<Pair<UnsignedLong, LatestMessage>> attesterLatestMessages =
        newAttestationEvent.getAttesterLatestMessages();

    attesterLatestMessages.forEach(pair -> latest_messages.put(pair.getLeft(), pair.getRight()));

    if (checkpoint != null) {
      checkpoint_states.put(checkpoint, state);
    }
    db.commit();
  }

  @Subscribe
  public void onNewSlot(SlotEvent slotEvent) {
    if (compute_start_slot_of_epoch(compute_epoch_of_slot(slotEvent.getSlot()))
        .equals(slotEvent.getSlot())) {
      time.set(slotEvent.getTime());
      // printDB();
    }
    db.commit();
  }

  // Print contents of DB

  @SuppressWarnings("ObjectToString")
  private void printDB() {
    System.out.println("time: " + time.get());
    System.out.println("justified checkpoint: " + justifiedCheckpoint.get());
    System.out.println("finalized checkpoint: " + finalizedCheckpoint.get());
    blocks.values().forEach(block -> System.out.println("block: " + block.toString()));
    block_states.values().forEach(state -> System.out.println("blockState: " + state.toString()));
    checkpoint_states
        .values()
        .forEach(checkpoint -> System.out.println("checkpointState: " + checkpoint.toString()));
    latest_messages
        .values()
        .forEach(
            latestMessage ->
                System.out.println("attesterLatestMessage: " + latestMessage.toString()));
  }
}
