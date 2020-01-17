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

package tech.pegasys.artemis.test.acceptance;

import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.artemis.test.acceptance.dsl.ArtemisNode;
import tech.pegasys.artemis.test.acceptance.dsl.ArtemisNode.Config;
import tech.pegasys.artemis.test.acceptance.dsl.tools.GenesisStateConfig;

public class SyncAcceptanceTest extends AcceptanceTestBase {
  private static final Logger LOG = LogManager.getLogger();

  @Test
  public void test() throws Exception {
    final int validatorCount = 2;
    final GenesisStateConfig genesisStateConfig = GenesisStateConfig.create(validatorCount);

    final ArtemisNode primaryNode =
        createArtemisNode(configurePrimaryNode(genesisStateConfig, validatorCount));
    final ArtemisNode lateJoiningNode =
        createArtemisNode(configureLateJoiningNode(genesisStateConfig, primaryNode));

    LOG.debug("Start first node");
    primaryNode.start();
    LOG.debug("Wait for genesis");
    primaryNode.waitForGenesis();
    LOG.debug("Wait for finalized block");
    primaryNode.waitForNewFinalization();
    LOG.debug("First node has finalized block");

    LOG.debug("Start second node");
    lateJoiningNode.start();
    LOG.debug("Wait for genesis");
    lateJoiningNode.waitForGenesis();
    LOG.debug("Wait second node to sync with first node.");
    lateJoiningNode.waitUntilInSyncWith(primaryNode);
  }

  private Consumer<Config> configurePrimaryNode(
      final GenesisStateConfig genesisConfig, final int validatorCount) {
    return c ->
        c.withGenesisConfig(genesisConfig)
            .withRealNetwork()
            .withInteropValidators(0, validatorCount);
  }

  private Consumer<Config> configureLateJoiningNode(
      final GenesisStateConfig genesisConfig, final ArtemisNode primaryNode) {
    return c ->
        c.withGenesisConfig(genesisConfig)
            .withRealNetwork()
            .withPeers(primaryNode)
            .withInteropValidators(0, 0);
  }
}
