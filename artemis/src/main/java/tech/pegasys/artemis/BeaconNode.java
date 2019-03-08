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

package tech.pegasys.artemis;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.Executors;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.data.provider.CSVProvider;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.services.ServiceController;
import tech.pegasys.artemis.services.beaconchain.BeaconChainService;
import tech.pegasys.artemis.services.chainstorage.ChainStorageService;
import tech.pegasys.artemis.services.powchain.PowchainService;
import tech.pegasys.artemis.util.cli.CommandLineArguments;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class BeaconNode {
  EventBus eventBus;
  String path;
  String filename;
  CommandLineArguments cliArgs;
  CommandLine commandLine;

  public BeaconNode(CommandLine commandLine, CommandLineArguments cliArgs) {
    this.eventBus = new AsyncEventBus(Executors.newCachedThreadPool());
    ;
    this.eventBus.register(this);
    this.path = "/Users/jonny/projects/consensys/pegasys/artemis/";
    this.filename = "artemis";
    this.cliArgs = cliArgs;
    this.commandLine = commandLine;
  }

  public void start() {
    try {

      if (commandLine.isUsageHelpRequested()) {
        commandLine.usage(System.out);
        return;
      }
      // set log level per CLI flags
      System.out.println("Setting logging level to " + cliArgs.getLoggingLevel().name());
      Configurator.setAllLevels("", cliArgs.getLoggingLevel());
      // Detect SIGTERM
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread() {
                @Override
                public void run() {
                  System.out.println("Artemis is shutting down");
                  ServiceController.stopAll(cliArgs);
                }
              });

      // Initialize services
      ServiceController.initAll(
          eventBus,
          cliArgs,
          BeaconChainService.class,
          PowchainService.class,
          ChainStorageService.class);
      // Start services
      ServiceController.startAll(cliArgs);

    } catch (Exception e) {
      System.out.println(e.toString());
    }
  }

  @Subscribe
  public void onDataEvent(RawRecord record) {
    BeaconBlock block = record.getBlock();
    BeaconState state = record.getState();
    Bytes32 block_root = HashTreeUtil.hash_tree_root(block.toBytes());
    TimeSeriesRecord tsRecord =
        new TimeSeriesRecord(
            record.getNodeTime(),
            record.getNodeSlot(),
            block_root,
            block.getState_root(),
            block.getParent_root());
    CSVProvider csvRecord = new CSVProvider(tsRecord);
    CSVProvider.output(path, filename, csvRecord);
  }
}
