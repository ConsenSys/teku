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

package tech.pegasys.teku.storage.storageSystem;

import com.google.common.eventbus.EventBus;
import java.nio.file.Path;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.server.ChainStorage;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabase;
import tech.pegasys.teku.util.config.StateStorageMode;

public class FileBackedStorageSystem extends AbstractStorageSystem implements StorageSystem {
  private final Path dataPath;

  private final EventBus eventBus;
  private final TrackingReorgEventChannel reorgEventChannel;

  private final CombinedChainDataClient combinedChainDataClient;
  private final Database database;

  public FileBackedStorageSystem(
      final Path dataPath,
      final EventBus eventBus,
      final TrackingReorgEventChannel reorgEventChannel,
      final Database database,
      final RecentChainData recentChainData,
      final CombinedChainDataClient combinedChainDataClient) {
    super(recentChainData);

    this.dataPath = dataPath;
    this.eventBus = eventBus;
    this.database = database;
    this.reorgEventChannel = reorgEventChannel;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  public static StorageSystem createV3StorageSystem(
      final Path dataPath, final StateStorageMode storageMode) {
    final RocksDbConfiguration rocksDbConfiguration =
        RocksDbConfiguration.withDataDirectory(dataPath);
    final Database database =
        RocksDbDatabase.createV3(new StubMetricsSystem(), rocksDbConfiguration, storageMode);

    try {
      final EventBus eventBus = new EventBus();

      // Create and start storage server
      final ChainStorage chainStorageServer = ChainStorage.create(eventBus, database);
      chainStorageServer.start();

      // Create recent chain data
      final FinalizedCheckpointChannel finalizedCheckpointChannel =
          new StubFinalizedCheckpointChannel();
      final TrackingReorgEventChannel reorgEventChannel = new TrackingReorgEventChannel();
      final RecentChainData recentChainData =
          StorageBackedRecentChainData.createImmediately(
              new NoOpMetricsSystem(),
              chainStorageServer,
              finalizedCheckpointChannel,
              reorgEventChannel,
              eventBus);

      // Create combined client
      final CombinedChainDataClient combinedChainDataClient =
          new CombinedChainDataClient(recentChainData, chainStorageServer);

      // Return storage system
      return new FileBackedStorageSystem(
          dataPath,
          eventBus,
          reorgEventChannel,
          database,
          recentChainData,
          combinedChainDataClient);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to initialize storage system", e);
    }
  }

  @Override
  public StorageSystem restarted(final StateStorageMode storageMode) {
    try {
      database.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return createV3StorageSystem(dataPath, storageMode);
  }

  @Override
  public RecentChainData recentChainData() {
    return recentChainData;
  }

  @Override
  public CombinedChainDataClient combinedChainDataClient() {
    return combinedChainDataClient;
  }

  @Override
  public EventBus eventBus() {
    return eventBus;
  }

  @Override
  public TrackingReorgEventChannel reorgEventChannel() {
    return reorgEventChannel;
  }
}
