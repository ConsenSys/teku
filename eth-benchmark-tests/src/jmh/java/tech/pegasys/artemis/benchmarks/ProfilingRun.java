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

package tech.pegasys.artemis.benchmarks;

import static org.mockito.Mockito.mock;

import com.google.common.eventbus.EventBus;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.benchmarks.gen.BlockIO;
import tech.pegasys.artemis.benchmarks.gen.BlockIO.Reader;
import tech.pegasys.artemis.benchmarks.gen.BlsKeyPairIO;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconStateRead;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

/** The test to be run manually for profiling block imports */
public class ProfilingRun {

  @Disabled
  @Test
  public void importBlocks() throws Exception {

    Constants.SLOTS_PER_EPOCH = 6;
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;

    int validatorsCount = 10 * 1024;

    String blocksFile =
        "/blocks/blocks_epoch_"
            + Constants.SLOTS_PER_EPOCH
            + "_validators_"
            + validatorsCount
            + ".ssz.gz";

    System.out.println("Generating keypairs...");
    //    List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(validatorsCount);
    //    List<BLSKeyPair> validatorKeys =
    // BlsKeyPairIO.createReaderWithDefaultSource().readAll(validatorsCount);
    List<BLSKeyPair> validatorKeys =
        BlsKeyPairIO.createReaderForResource("/bls-key-pairs/bls-key-pairs-100k-seed-0.txt.gz")
            .readAll(validatorsCount);

    EventBus localEventBus = mock(EventBus.class);
    ChainStorageClient localStorage = ChainStorageClient.memoryOnlyClient(localEventBus);
    BeaconChainUtil localChain = BeaconChainUtil.create(localStorage, validatorKeys, false);
    localChain.initializeStorage();

    BlockImporter blockImporter = new BlockImporter(localStorage, localEventBus);

    System.out.println("Start blocks import from " + blocksFile);
    try (Reader blockReader = BlockIO.createResourceReader(blocksFile)) {
      for (SignedBeaconBlock block : blockReader) {
        long s = System.currentTimeMillis();
        localChain.setSlot(block.getSlot());
        BlockImportResult result = blockImporter.importBlock(block);
//        compareHashes(result.getBlockProcessingRecord().getPostState());
        System.out.println(
            "Imported block at #"
                + block.getSlot()
                + " in "
                + (System.currentTimeMillis() - s)
                + " ms: "
                + result);
      }
    }
  }

  @Test
  void compareHashes(BeaconStateRead s1) {
    for (int i = 0; i < s1.size(); i++) {
      Bytes32 hash = s1.get(i).hashTreeRoot();
      System.out.println(i + ": " + hash);
    }
    System.out.println("BS: " + s1.hash_tree_root());
    System.out.println("BS: " + old_hash_tree_root(s1));
    System.out.println("getEth1_data: " + s1.getEth1_data().hash_tree_root());
    System.out.println("getEth1_data: " + s1.getEth1_data().hash_tree_root());
    System.out.println("getValidators: " + s1.getValidators().hash_tree_root());
    System.out.println(
        "getValidators: " + HashTreeUtil.hash_tree_root(HashTreeUtil.SSZTypes.LIST_OF_COMPOSITE,
            Constants.VALIDATOR_REGISTRY_LIMIT, s1.getValidators()));
    System.out.println("getBlock_roots: " + s1.getBlock_roots().hash_tree_root());
    System.out.println("getBlock_roots: " + HashTreeUtil.hash_tree_root(
        SSZTypes.VECTOR_OF_COMPOSITE, s1.getBlock_roots()));

    System.out.println("getHistorical_roots: " + s1.getHistorical_roots().hash_tree_root());
    System.out.println("getHistorical_roots: " + HashTreeUtil.hash_tree_root(
        SSZTypes.LIST_OF_COMPOSITE, Constants.HISTORICAL_ROOTS_LIMIT, s1.getHistorical_roots()));

    System.out.println("getEth1_data_votes: " + s1.getEth1_data_votes().hash_tree_root());
    System.out.println("getEth1_data_votes: " + HashTreeUtil.hash_tree_root(
        SSZTypes.LIST_OF_COMPOSITE, Constants.SLOTS_PER_ETH1_VOTING_PERIOD, s1.getEth1_data_votes()));

    System.out.println("getBalances: " + s1.getBalances().hash_tree_root());
    System.out.println("getBalances: " + HashTreeUtil.hash_tree_root_list_ul(
        Constants.VALIDATOR_REGISTRY_LIMIT,
        s1.getBalances().stream()
            .map(item -> SSZ.encodeUInt64(item.longValue()))
            .collect(Collectors.toList())));


    System.out.println("getJustification_bits: " + HashTreeUtil.hash_tree_root_bitvector(s1.getJustification_bits()));
  }

  public Bytes32 old_hash_tree_root(BeaconStateRead s) {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            // Versioning
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(s.getGenesis_time().longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(s.getSlot().longValue())),
            s.getFork().hash_tree_root(),

            // History
            s.getLatest_block_header().hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, s.getBlock_roots()),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, s.getState_roots()),
            HashTreeUtil.hash_tree_root_list_bytes(
                Constants.HISTORICAL_ROOTS_LIMIT, s.getHistorical_roots()),

            // Ethereum 1.0 chain data
            s.getEth1_data().hash_tree_root(),
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE,
                Constants.SLOTS_PER_ETH1_VOTING_PERIOD,
                s.getEth1_data_votes()),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(s.getEth1_deposit_index().longValue())),

            // Validator registry
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE, Constants.VALIDATOR_REGISTRY_LIMIT, s.getValidators()),
            HashTreeUtil.hash_tree_root_list_ul(
                Constants.VALIDATOR_REGISTRY_LIMIT,
                s.getBalances().stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList())),

            // Randomness
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, s.getRandao_mixes()),

            // Slashings
            HashTreeUtil.hash_tree_root_vector_unsigned_long(s.getSlashings()),

            // Attestations
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE,
                Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
                s.getPrevious_epoch_attestations()),
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE,
                Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
                s.getCurrent_epoch_attestations()),

            // Finality
            HashTreeUtil.hash_tree_root_bitvector(s.getJustification_bits()),
            s.getPrevious_justified_checkpoint().hash_tree_root(),
            s.getCurrent_justified_checkpoint().hash_tree_root(),
            s.getFinalized_checkpoint().hash_tree_root()));
  }
}
