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

package tech.pegasys.artemis.statetransition;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.artemis.datastructures.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.crypto.Hash;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.state.CrosslinkRecord;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.PendingAttestationRecord;
import tech.pegasys.artemis.datastructures.state.ShardCommittee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.state.ValidatorRegistryDeltaBlock;
import tech.pegasys.artemis.datastructures.state.Validators;
import tech.pegasys.artemis.statetransition.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.util.TreeHashUtil;

public class BeaconState {

  // Misc
  private long slot;
  private long genesis_time;
  private Fork fork;

  // Validator registry
  private Validators validator_registry;
  private ArrayList<Double> validator_balances;
  private long validator_registry_update_epoch;
  private Bytes32 validator_registry_delta_chain_tip;

  // Randomness and committees
  private ArrayList<Bytes32> latest_randao_mixes;
  private long previous_epoch_start_shard;
  private long current_epoch_start_shard;
  private long previous_calculation_epoch;
  private long current_calculation_epoch;
  private Bytes32 previous_epoch_seed;
  private Bytes32 current_epoch_seed;

  // Finality
  private long previous_justified_slot;
  private long justified_slot;
  private long justification_bitfield;
  private long finalized_slot;

  // Recent state
  private ArrayList<CrosslinkRecord> latest_crosslinks;
  private ArrayList<Bytes32> latest_block_roots = new ArrayList<>();
  private ArrayList<Double> latest_penalized_balances;
  private ArrayList<PendingAttestationRecord> latest_attestations;
  private ArrayList<Bytes32> batched_block_roots = new ArrayList<>();

  // Ethereum 1.0 chain data
  private Eth1Data latest_eth1_data;
  private List<Eth1DataVote> eth1_data_votes;

  // Default Constructor
  public BeaconState() {
    // TODO: temp to allow it to run in demo mode
    this.slot = 0;
  }

  public static BeaconState deepCopy(BeaconState state) {
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(Bytes32.class, new InterfaceAdapter<Bytes32>())
            .registerTypeAdapter(Bytes48.class, new InterfaceAdapter<Bytes48>())
            .create();
    return gson.fromJson(gson.toJson(state), BeaconState.class);
  }

  public BeaconState(
      // Misc
      long slot,
      long genesis_time,
      Fork fork,
      // Validator registry
      Validators validator_registry,
      ArrayList<Double> validator_balances,
      long validator_registry_update_epoch,
      Bytes32 validator_registry_delta_chain_tip,
      // Randomness and committees
      ArrayList<Bytes32> latest_randao_mixes,
      long previous_epoch_start_shard,
      long current_epoch_start_shard,
      long previous_calculation_epoch,
      long current_calculation_epoch,
      Bytes32 previous_epoch_seed,
      Bytes32 current_epoch_seed,

      // Finality
      long previous_justified_slot,
      long justified_slot,
      long justification_bitfield,
      long finalized_slot,
      // Recent state
      ArrayList<CrosslinkRecord> latest_crosslinks,
      ArrayList<Bytes32> latest_block_roots,
      ArrayList<Double> latest_penalized_balances,
      ArrayList<PendingAttestationRecord> latest_attestations,
      ArrayList<Bytes32> batched_block_roots,
      // Ethereum 1.0 chain data
      Eth1Data latest_eth1_data,
      ArrayList<Eth1DataVote> eth1_data_votes) {

    // Misc
    this.slot = slot;
    this.genesis_time = genesis_time;
    this.fork = fork;

    // Validator registry
    this.validator_registry = validator_registry;
    this.validator_balances = validator_balances;
    this.validator_registry_update_epoch = validator_registry_update_epoch;
    this.validator_registry_delta_chain_tip = validator_registry_delta_chain_tip;

    // Randomness and committees
    this.latest_randao_mixes = latest_randao_mixes;
    this.previous_epoch_start_shard = previous_epoch_start_shard;
    this.current_epoch_start_shard = current_epoch_start_shard;
    this.previous_calculation_epoch = previous_calculation_epoch;
    this.current_calculation_epoch = current_calculation_epoch;
    this.previous_epoch_seed = previous_epoch_seed;
    this.current_epoch_seed = current_epoch_seed;

    // Finality
    this.previous_justified_slot = previous_justified_slot;
    this.justified_slot = justified_slot;
    this.justification_bitfield = justification_bitfield;
    this.finalized_slot = finalized_slot;

    // Recent state
    this.latest_crosslinks = latest_crosslinks;
    this.latest_block_roots = latest_block_roots;
    this.latest_penalized_balances = latest_penalized_balances;
    this.latest_attestations = latest_attestations;
    this.batched_block_roots = batched_block_roots;

    // Ethereum 1.0 chain data
    this.latest_eth1_data = latest_eth1_data;
    this.eth1_data_votes = eth1_data_votes;
  }

  @VisibleForTesting
  @SuppressWarnings("ModifiedButNotUsed")
  public BeaconState get_initial_beacon_state(
      ArrayList<Deposit> initial_validator_deposits, int genesis_time, Eth1Data latest_eth1_data) {

    // TODO: this needs to be checked against 0.1 spec
    ArrayList<Bytes32> latest_randao_mixes = new ArrayList<>();
    ArrayList<Bytes32> latest_block_roots = new ArrayList<>();
    ArrayList<CrosslinkRecord> latest_crosslinks = new ArrayList<>(SHARD_COUNT);

    for (int i = 0; i < SHARD_COUNT; i++) {
      latest_crosslinks.add(new CrosslinkRecord(Bytes32.ZERO, UnsignedLong.valueOf(GENESIS_SLOT)));
    }

    // TODO after update v0.1 constants no longer exist
    BeaconState state = new BeaconState();
    //    BeaconState state =
    //        new BeaconState(
    //            // Misc
    //            INITIAL_SLOT_NUMBER,
    //            genesis_time,
    //            new ForkData(
    //                UnsignedLong.valueOf(INITIAL_FORK_VERSION),
    //                UnsignedLong.valueOf(INITIAL_FORK_VERSION),
    //                UnsignedLong.valueOf(INITIAL_SLOT_NUMBER)),
    //
    //            // Validator registry
    //            new Validators(),
    //            new ArrayList<>(),
    //            INITIAL_SLOT_NUMBER,
    //            0,
    //            Bytes32.ZERO,
    //
    //            // Randomness and committees
    //            latest_randao_mixes,
    //            latest_vdf_outputs,
    //            new ArrayList<>(),
    //            new ArrayList<>(),
    //            new ArrayList<>(),
    //
    //            // Finality
    //            INITIAL_SLOT_NUMBER,
    //            INITIAL_SLOT_NUMBER,
    //            0,
    //            INITIAL_SLOT_NUMBER,
    //
    //            // Recent state
    //            latest_crosslinks,
    //            latest_block_roots,
    //            new ArrayList<>(),
    //            new ArrayList<>(),
    //            new ArrayList<>(),
    //
    //            // PoW receipt root
    //            processed_pow_receipt_root,
    //            new ArrayList<>());

    // handle initial deposits and activations
    for (Deposit validator_deposit : initial_validator_deposits) {
      DepositInput deposit_input = validator_deposit.getDeposit_data().getDeposit_input();
      int validator_index =
          process_deposit(
              state,
              deposit_input.getPubkey(),
              validator_deposit.getDeposit_data().getValue().longValue(),
              deposit_input.getProof_of_possession(),
              deposit_input.getWithdrawal_credentials(),
              deposit_input.getRandao_commitment(),
              deposit_input.getPoc_commitment());
      // TODO after update v0.1 constants no longer exist
      //      if (state.getValidator_balances().get(validator_index) >= (MAX_DEPOSIT *
      // GWEI_PER_ETH)) {
      // TODO updates in v0.1 to constants removed necessary values
      //        //        update_validator_status(state, validator_index, ACTIVE);
      //      }
    }

    state.current_epoch_seed = BeaconStateUtil.generate_seed(state, GENESIS_EPOCH);

    return state;
  }

  /**
   * @param validators
   * @param current_slot
   * @return The minimum empty validator index.
   */
  private int min_empty_validator_index(
      ArrayList<Validator> validators, ArrayList<Double> validator_balances, int current_slot) {
    for (int i = 0; i < validators.size(); i++) {
      Validator v = validators.get(i);
      double vbal = validator_balances.get(i);
      // todo getLatest_status_change_slot method no longer exists following the recent update
      //      if (vbal == 0
      //          && v.getLatest_status_change_slot().longValue() + ZERO_BALANCE_VALIDATOR_TTL
      //              <= current_slot) {
      return i;
      //      }
    }
    return validators.size();
  }

  /**
   * @param state
   * @param pubkey
   * @param proof_of_possession
   * @param withdrawal_credentials
   * @param randao_commitment
   * @return
   */
  private boolean validate_proof_of_possession(
      BeaconState state,
      Bytes48 pubkey,
      List<Bytes48> proof_of_possession,
      Bytes32 withdrawal_credentials,
      Bytes32 randao_commitment,
      Bytes32 poc_commitment) {
    DepositInput proof_of_possession_data =
        new DepositInput(
            poc_commitment, proof_of_possession, pubkey, randao_commitment, withdrawal_credentials);

    List<Bytes48> signature =
        Arrays.asList(
            Bytes48.leftPad(proof_of_possession.get(0)),
            Bytes48.leftPad(proof_of_possession.get(1)));
    UnsignedLong domain =
        UnsignedLong.valueOf(get_domain(state.fork, toIntExact(state.getSlot()), DOMAIN_DEPOSIT));
    return bls_verify(
        pubkey, TreeHashUtil.hash_tree_root(proof_of_possession_data.toBytes()), signature, domain);
  }

  /**
   * Process a deposit from Ethereum 1.0. Note that this function mutates 'state'.
   *
   * @param state
   * @param pubkey
   * @param deposit
   * @param proof_of_possession
   * @param withdrawal_credentials
   * @param randao_commitment
   * @return
   */
  public int process_deposit(
      BeaconState state,
      Bytes48 pubkey,
      double deposit,
      List<Bytes48> proof_of_possession,
      Bytes32 withdrawal_credentials,
      Bytes32 randao_commitment,
      Bytes32 poc_commitment) {
    assert validate_proof_of_possession(
        state,
        pubkey,
        proof_of_possession,
        withdrawal_credentials,
        randao_commitment,
        poc_commitment);

    Bytes48[] validator_pubkeys = new Bytes48[state.validator_registry.size()];
    for (int i = 0; i < validator_pubkeys.length; i++) {
      validator_pubkeys[i] = state.validator_registry.get(i).getPubkey();
    }

    int index;

    if (indexOfPubkey(validator_pubkeys, pubkey) == -1) {
      // Add new validator
      Validator validator = new Validator();
      // TODO Validator constructor changes after the the 0.1 update
      // empty validator record declared in it's place
      //          new Validator(
      //              pubkey,
      //              withdrawal_credentials,
      //              randao_commitment,
      //              UnsignedLong.ZERO,
      //              UnsignedLong.valueOf(PENDING_ACTIVATION),
      //              UnsignedLong.valueOf(state.getSlot()),
      //              UnsignedLong.ZERO,
      //              UnsignedLong.ZERO,
      //              UnsignedLong.ZERO);

      ArrayList<Validator> validators_copy = new ArrayList<>(validator_registry);
      index = min_empty_validator_index(validators_copy, validator_balances, toIntExact(slot));
      if (index == validators_copy.size()) {
        state.validator_registry.add(validator);
        state.validator_balances.add(deposit);
        index = state.validator_registry.size() - 1;
      } else {
        state.validator_registry.set(index, validator);
        state.validator_balances.set(index, deposit);
      }
    } else {
      // Increase balance by deposit
      index = indexOfPubkey(validator_pubkeys, pubkey);
      Validator validator = state.validator_registry.get(index);
      assert validator.getWithdrawal_credentials().equals(withdrawal_credentials);

      state.validator_balances.set(index, state.validator_balances.get(index) + deposit);
    }

    return index;
  }

  /**
   * Helper function to find the index of the pubkey in the array of validators' pubkeys.
   *
   * @param validator_pubkeys
   * @param pubkey
   * @return The index of the pubkey.
   */
  private int indexOfPubkey(Bytes48[] validator_pubkeys, Bytes48 pubkey) {
    for (int i = 0; i < validator_pubkeys.length; i++) {
      if (validator_pubkeys[i].equals(pubkey)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @param fork
   * @param slot
   * @param domain_type
   * @return
   */
  private int get_domain(Fork fork, int slot, int domain_type) {
    return get_fork_version(fork, slot) * (int) Math.pow(2, 32) + domain_type;
  }

  /**
   * @param fork
   * @param slot
   * @return
   */
  private int get_fork_version(Fork fork, int slot) {
    if (slot < fork.getFork_slot().longValue()) {
      return toIntExact(fork.getPre_fork_version().longValue());
    } else {
      return toIntExact(fork.getPost_fork_version().longValue());
    }
  }

  /**
   * Activate the validator with the given 'index'. Note that this function mutates 'state'.
   *
   * @param index The index of the validator.
   */
  @VisibleForTesting
  public void activate_validator(BeaconState state, int index, boolean is_genesis) {
    Validator validator = validator_registry.get(index);
    UnsignedLong activation_epoch = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);
    long current_epoch = BeaconStateUtil.get_current_epoch(this);
    if (UnsignedLong.valueOf(current_epoch).compareTo(UnsignedLong.valueOf(Constants.GENESIS_SLOT))
        > 0) {
      activation_epoch =
          BeaconStateUtil.get_entry_exit_effect_epoch(UnsignedLong.valueOf(current_epoch));
      validator.setActivation_epoch(activation_epoch);
    }
  }

  /**
   * Returns the beacon proposer index for the 'slot'.
   *
   * @param state
   * @param slot
   * @return
   */
  public static Integer get_beacon_proposer_index(BeaconState state, int slot) {
    ShardCommittee first_committee =
        BeaconStateUtil.get_crosslink_committees_at_slot(state, slot).get(0);
    return first_committee.getCommittee().get(slot % first_committee.getCommitteeSize());
  }

  /**
   * Returns the participant indices at for the 'attestation_data' and 'participation_bitfield'.
   *
   * @param state
   * @param attestation_data
   * @param participation_bitfield
   * @return
   */
  public static ArrayList<Integer> get_attestation_participants(
      BeaconState state, AttestationData attestation_data, byte[] participation_bitfield) {
    // Find the relevant committee
    ArrayList<ShardCommittee> crosslink_committees =
        BeaconStateUtil.get_crosslink_committees_at_slot(
            state, toIntExact(attestation_data.getSlot()));

    // TODO: assert attestation_data.shard in [shard for _, shard in crosslink_committees]

    ShardCommittee crosslink_committee = null;
    for (ShardCommittee curr_crosslink_committee : crosslink_committees) {
      if (curr_crosslink_committee.getShard().equals(attestation_data.getShard())) {
        crosslink_committee = curr_crosslink_committee;
        break;
      }
    }
    assert crosslink_committee != null;
    assert participation_bitfield.length == ceil_div8(crosslink_committee.getCommitteeSize());

    // Find the participating attesters in the committee
    ArrayList<Integer> participants = new ArrayList<>();
    for (int i = 0; i < crosslink_committee.getCommitteeSize(); i++) {
      int participation_bit = (participation_bitfield[i / 8] >> (7 - (i % 8))) % 2;
      if (participation_bit == 1) {
        participants.add(crosslink_committee.getCommittee().get(i));
      }
    }
    return participants;
  }

  /**
   * Return the smallest integer r such that r * div >= 8.
   *
   * @param div
   * @return
   */
  private static int ceil_div8(int div) {
    checkArgument(div > 0, "Expected positive div but got %s", div);
    return (int) Math.ceil(8.0 / div);
  }

  /**
   * Assumes 'attestation_data_1' is distinct from 'attestation_data_2'.
   *
   * @param attestation_data_1
   * @param attestation_data_2
   * @return True if the provided 'AttestationData' are slashable due to a 'double vote'.
   */
  private boolean is_double_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    long target_epoch_1 = attestation_data_1.getSlot() / EPOCH_LENGTH;
    long target_epoch_2 = attestation_data_2.getSlot() / EPOCH_LENGTH;
    return target_epoch_1 == target_epoch_2;
  }

  /**
   * Note: parameter order matters as this function only checks that 'attestation_data_1' surrounds
   * 'attestation_data_2'.
   *
   * @param attestation_data_1
   * @param attestation_data_2
   * @return True if the provided 'AttestationData' are slashable due to a 'surround vote'.
   */
  private boolean is_surround_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    long source_epoch_1 = attestation_data_1.getJustified_slot().longValue() / EPOCH_LENGTH;
    long source_epoch_2 = attestation_data_2.getJustified_slot().longValue() / EPOCH_LENGTH;
    long target_epoch_1 = attestation_data_1.getSlot() / EPOCH_LENGTH;
    long target_epoch_2 = attestation_data_2.getSlot() / EPOCH_LENGTH;
    return source_epoch_1 < source_epoch_2
        && (source_epoch_2 + 1 == target_epoch_2)
        && target_epoch_2 < target_epoch_1;
  }

  /**
   * The largest integer 'x' such that 'x**2' is less than 'n'.
   *
   * @param n highest bound of x.
   * @return x
   */
  private int integer_squareroot(int n) {
    int x = n;
    int y = (x + 1) / 2;
    while (y < x) {
      x = y;
      y = (x + n / x) / 2;
    }
    return x;
  }

  /**
   * Compute the next root in the validator registry delta chain.
   *
   * @param current_validator_registry_delta_chain_tip
   * @param validator_index
   * @param pubkey
   * @param flag
   * @return The next root.
   */
  private Bytes32 get_new_validator_registry_delta_chain_tip(
      Bytes32 current_validator_registry_delta_chain_tip,
      int validator_index,
      Bytes48 pubkey,
      int flag,
      UnsignedLong slot) {
    return Hash.keccak256(
        TreeHashUtil.hash_tree_root(
            new ValidatorRegistryDeltaBlock(
                    UnsignedLong.valueOf(flag),
                    current_validator_registry_delta_chain_tip,
                    pubkey,
                    slot,
                    validator_index)
                .toBytes()));
  }
  /**
   * Returns the effective balance (also known as "balance at stake") for a 'validator' with the
   * given 'index'.
   *
   * @param state The BeaconState.
   * @param index The index at which the validator is at.
   * @return The effective balance.
   */
  private double get_effective_balance(BeaconState state, int index) {
    return Math.min(state.validator_balances.get(index).intValue(), Constants.MAX_DEPOSIT_AMOUNT);
  }

  public Bytes32 getPrevious_epoch_randao_mix() {
    // todo
    return null;
  }

  public int getPrevious_epoch_calculation_slot() {
    // todo
    return 0;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Eth1Data getLatest_eth1_data() {
    return this.latest_eth1_data;
  }

  public void setLatest_eth1_data(Eth1Data data) {
    this.latest_eth1_data = data;
  }

  public List<Eth1DataVote> getEth1_data_votes() {
    return this.eth1_data_votes;
  }

  public void setEth1_data_votes(List<Eth1DataVote> votes) {
    this.eth1_data_votes = votes;
  }

  public long getSlot() {
    return this.slot;
  }

  public void setSlot(long slot) {
    this.slot = slot;
  }

  public void incrementSlot() {
    this.slot++;
  }

  public long getGenesis_time() {
    return genesis_time;
  }

  public void setGenesis_time(long genesis_time) {
    this.genesis_time = genesis_time;
  }

  public Fork getFork() {
    return fork;
  }

  public void setFork(Fork fork) {
    this.fork = fork;
  }

  public Validators getValidator_registry() {
    return validator_registry;
  }

  public void setValidator_registry(ArrayList<Validator> validator_registry) {
    this.validator_registry = new Validators(validator_registry);
  }

  public ArrayList<Double> getValidator_balances() {
    return validator_balances;
  }

  public void setValidator_balances(ArrayList<Double> validator_balances) {
    this.validator_balances = validator_balances;
  }

  public long getValidator_registry_update_epoch() {
    return validator_registry_update_epoch;
  }

  public void setValidator_registry_update_epoch(long validator_registry_update_epoch) {
    this.validator_registry_update_epoch = validator_registry_update_epoch;
  }

  public Bytes32 getValidator_registry_delta_chain_tip() {
    return validator_registry_delta_chain_tip;
  }

  public void setValidator_registry_delta_chain_tip(Bytes32 validator_registry_delta_chain_tip) {
    this.validator_registry_delta_chain_tip = validator_registry_delta_chain_tip;
  }

  public long getPrevious_epoch_start_shard() {
    return previous_epoch_start_shard;
  }

  public void setPrevious_epoch_start_shard(long previous_epoch_start_shard) {
    this.previous_epoch_start_shard = previous_epoch_start_shard;
  }

  public long getCurrent_epoch_start_shard() {
    return current_epoch_start_shard;
  }

  public void setCurrent_epoch_start_shard(long current_epoch_start_shard) {
    this.current_epoch_start_shard = current_epoch_start_shard;
  }

  public long getPrevious_calculation_epoch() {
    return previous_calculation_epoch;
  }

  public void setPrevious_calculation_epoch(long previous_calculation_epoch) {
    this.previous_calculation_epoch = previous_calculation_epoch;
  }

  public long getCurrent_calculation_epoch() {
    return current_calculation_epoch;
  }

  public void setCurrent_calculation_epoch(long current_calculation_epoch) {
    this.current_calculation_epoch = current_calculation_epoch;
  }

  public Bytes32 getPrevious_epoch_seed() {
    return previous_epoch_seed;
  }

  public void setPrevious_epoch_seed(Bytes32 previous_epoch_seed) {
    this.previous_epoch_seed = previous_epoch_seed;
  }

  public Bytes32 getCurrent_epoch_seed() {
    return current_epoch_seed;
  }

  public void setCurrent_epoch_seed(Bytes32 current_epoch_seed) {
    this.current_epoch_seed = current_epoch_seed;
  }

  public long getPrevious_justified_slot() {
    return previous_justified_slot;
  }

  public void setPrevious_justified_slot(long previous_justified_slot) {
    this.previous_justified_slot = previous_justified_slot;
  }

  public long getJustification_bitfield() {
    return justification_bitfield;
  }

  public void setJustification_bitfield(long justification_bitfield) {
    this.justification_bitfield = justification_bitfield;
  }

  public ArrayList<CrosslinkRecord> getLatest_crosslinks() {
    return latest_crosslinks;
  }

  public void setLatest_crosslinks(ArrayList<CrosslinkRecord> latest_crosslinks) {
    this.latest_crosslinks = latest_crosslinks;
  }

  public ArrayList<Bytes32> getLatest_block_roots() {
    return latest_block_roots;
  }

  public void setLatest_block_roots(ArrayList<Bytes32> latest_block_roots) {
    this.latest_block_roots = latest_block_roots;
  }

  public ArrayList<PendingAttestationRecord> getLatest_attestations() {
    return latest_attestations;
  }

  public void setLatest_attestations(ArrayList<PendingAttestationRecord> latest_attestations) {
    this.latest_attestations = latest_attestations;
  }

  public ArrayList<Bytes32> getBatched_block_roots() {
    return batched_block_roots;
  }

  public void setBatched_block_roots(ArrayList<Bytes32> batched_block_roots) {
    this.batched_block_roots = batched_block_roots;
  }

  public long getJustified_slot() {
    return justified_slot;
  }

  public void setJustified_slot(long justified_slot) {
    this.justified_slot = justified_slot;
  }

  public long getFinalized_slot() {
    return finalized_slot;
  }

  public void setFinalized_slot(long finalized_slot) {
    this.finalized_slot = finalized_slot;
  }

  public ArrayList<Double> getLatest_penalized_balances() {
    return latest_penalized_balances;
  }

  public void setLatest_penalized_balances(ArrayList<Double> latest_penalized_balances) {
    this.latest_penalized_balances = latest_penalized_balances;
  }

  public ArrayList<Bytes32> getLatest_randao_mixes() {
    return latest_randao_mixes;
  }

  public void setLatest_randao_mixes(ArrayList<Bytes32> latest_randao_mixes) {
    this.latest_randao_mixes = latest_randao_mixes;
  }

  public void updateBatched_block_roots() {
    batched_block_roots.add(BeaconStateUtil.merkle_root(latest_block_roots));
  }
}
