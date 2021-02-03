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

package tech.pegasys.teku.reference.phase0.shuffling;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.util.CommitteeUtil;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.ConstantsLoader;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.spec.SpecConfiguration;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.constants.SpecConstants;

public class ShufflingTestExecutor implements TestExecutor {
  private final SpecConstants specConstants = ConstantsLoader.loadConstants("minimal");
  private final SpecConfiguration specConfiguration = SpecConfiguration.builder().constants(specConstants).build();
  private final SpecProvider specProvider = SpecProvider.create(specConfiguration);

  final tech.pegasys.teku.spec.util.CommitteeUtil committeeUtil =
      specProvider.atSlot(UInt64.ZERO).getCommitteeUtil();
  public static final ImmutableMap<String, TestExecutor> SHUFFLING_TEST_TYPES =
      ImmutableMap.of("shuffling", new ShufflingTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final ShufflingData shufflingData =
        loadYaml(testDefinition, "mapping.yaml", ShufflingData.class);
    final Bytes32 seed = Bytes32.fromHexString(shufflingData.getSeed());
    IntStream.range(0, shufflingData.getCount())
        .forEach(
            index ->
                assertThat(
                        committeeUtil.computeShuffledIndex(index, shufflingData.getCount(), seed))
                    .isEqualTo(shufflingData.getMapping(index)));

    final int[] inputs = IntStream.range(0, shufflingData.getCount()).toArray();
    CommitteeUtil.shuffle_list(inputs, seed);
    assertThat(inputs).isEqualTo(shufflingData.getMapping());
  }

  private static final class ShufflingData {
    @JsonProperty(value = "seed", required = true)
    private String seed;

    @JsonProperty(value = "count", required = true)
    private int count;

    @JsonProperty(value = "mapping", required = true)
    private int[] mapping;

    public String getSeed() {
      return seed;
    }

    public int getCount() {
      return count;
    }

    public int[] getMapping() {
      return mapping;
    }

    public int getMapping(int index) {
      return mapping[index];
    }
  }
}
