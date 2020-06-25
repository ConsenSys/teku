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

package tech.pegasys.teku.core.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;

public class BlockProviderTest {
  private final ChainBuilder chainBuilder = ChainBuilder.createDefault();

  @Test
  void withKnownBlocks_withEmptyProvider()
      throws StateTransitionException, ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(2);
    final Map<Bytes32, SignedBeaconBlock> knownBlocks =
        chainBuilder
            .streamBlocksAndStates()
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));

    final BlockProvider provider = BlockProvider.withKnownBlocks(BlockProvider.NOOP, knownBlocks);
    for (Bytes32 root : knownBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(knownBlocks.get(root));
    }
  }

  @Test
  void withKnownBlocks_withNonEmptyProvider()
      throws StateTransitionException, ExecutionException, InterruptedException {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);

    final Map<Bytes32, SignedBeaconBlock> knownBlocks =
        chainBuilder
            .streamBlocksAndStates(0, 5)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));
    final Map<Bytes32, SignedBeaconBlock> otherBlocks =
        chainBuilder
            .streamBlocksAndStates(6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getBlock));

    final BlockProvider origProvider = BlockProviderFactory.fromMap(otherBlocks);
    final BlockProvider provider = BlockProvider.withKnownBlocks(origProvider, knownBlocks);

    // Pull known blocks
    for (Bytes32 root : knownBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(knownBlocks.get(root));
    }
    // Pull blocks from original provider
    for (Bytes32 root : otherBlocks.keySet()) {
      assertThat(provider.getBlock(root).get()).contains(otherBlocks.get(root));
    }
  }
}
