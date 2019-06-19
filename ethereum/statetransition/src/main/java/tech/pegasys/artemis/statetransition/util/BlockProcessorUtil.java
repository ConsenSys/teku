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

package tech.pegasys.artemis.statetransition.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.BLS_WITHDRAWAL_PREFIX_BYTE;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_ATTESTATION;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_BEACON_BLOCK;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_RANDAO;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_TRANSFER;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_VOLUNTARY_EXIT;
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_ATTESTER_SLASHINGS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EPOCHS_PER_CROSSLINK;
import static tech.pegasys.artemis.datastructures.Constants.MAX_PROPOSER_SLASHINGS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_TRANSFERS;
import static tech.pegasys.artemis.datastructures.Constants.MIN_DEPOSIT_AMOUNT;
import static tech.pegasys.artemis.datastructures.Constants.PERSISTENT_COMMITTEE_PERIOD;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_attestation_participants;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_bitfield_bit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_crosslink_committees_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_randao_mix;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.initiate_validator_exit;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_double_vote;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.is_surround_vote;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.min;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slash_validator;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.slot_to_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.verify_merkle_branch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.verify_slashable_attestation;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.is_active_validator;
import static tech.pegasys.artemis.util.bls.BLSAggregate.bls_aggregate_pubkeys;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify_multiple;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SlashableAttestation;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.BeaconBlockUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public final class BlockProcessorUtil {

  private static final ALogger LOG = new ALogger(BlockProcessorUtil.class.getName());

  // @v0.7.1
  public static void process_block_header(BeaconState state, BeaconBlock block) {
    checkArgument(block.getSlot().equals(state.getSlot()), "Verify that the slots match");
    checkArgument(
        block
            .getParent_root()
            .equals(state.getLatest_block_header().signing_root("signature")),
        "Verify that the parent matches");

    // Save the current block as the new latest block
    state.setLatest_block_header(new BeaconBlockHeader(
            block.getSlot(),
            block.getParent_root(),
            Bytes32.ZERO,
            block.getBody().hash_tree_root(),
            BLSSignature.empty()
    ));

    // Only if we are processing blocks (not proposing them)
    if (!block.getState_root().equals(Bytes32.ZERO)) {
      Validator proposer =
          state.getValidator_registry().get(get_beacon_proposer_index(state, state.getSlot()));
      checkArgument(!proposer.isSlashed(), "Verify proposer is not slashed");
      checkArgument(
          bls_verify(
              proposer.getPubkey(),
              block.signing_root("signature"),
              block.getSignature(),
              get_domain(state, DOMAIN_BEACON_PROPOSER)),
          "Verify proposer signature");
    }
  }

  // @v0.7.1
  public static void process_randao(BeaconState state, BeaconBlockBody body) {
    Validator proposer =
        state.getValidator_registry().get(get_beacon_proposer_index(state));

    checkArgument(
        bls_verify(
            proposer.getPubkey(),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(get_current_epoch(state).longValue())),
            body.getRandao_reveal(),
            get_domain(state, DOMAIN_RANDAO)),
        "Verify that the provided randao value is valid");

    // Mix it in
    int index =
        get_current_epoch(state)
            .mod(UnsignedLong.valueOf(Constants.LATEST_RANDAO_MIXES_LENGTH))
            .intValue();
    Bytes32 newRandaoMix =
        get_randao_mix(state, get_current_epoch(state))
            .xor(Hash.keccak256(body.getRandao_reveal().toBytes()));
    state.getLatest_randao_mixes().set(index, newRandaoMix);
  }

  // @v0.7.1
  public static void process_eth1_data(BeaconState state, BeaconBlockBody body) {
    state.getEth1_data_votes().add(body.getEth1_data());
    long vote_count = state.getEth1_data_votes().stream().filter(item -> item.equals(body.getEth1_data())).count();
    if (vote_count * 2 > SLOTS_PER_ETH1_VOTING_PERIOD) {
      state.setLatest_eth1_data(body.getEth1_data());
    }
  }

  // @v0.7.1
  public static void process_operations(BeaconState state, BeaconBlockBody body)
          throws BlockProcessingException {
    try {

      checkArgument(body.getDeposits().size() == Math.min(MAX_DEPOSITS, toIntExact(state.getLatest_eth1_data().getDeposit_count().minus(state.getDeposit_index()).longValue())),
            "Verify that outstanding deposits are processed up to the maximum number of deposits");
    Set transfersSet = new HashSet(body.getTransfers());
    checkArgument(body.getTransfers().size() == transfersSet.size(), "Verify that there are no duplicate transfers");

    process_proposer_slashings(state, body.getProposer_slashings());
    process_attester_slashings(state, body.getAttester_slashings());
    process_attestations(state, body.getAttestations());
    process_deposits(state, body.getDeposits());
    process_voluntary_exits(state, body.getVoluntary_exits());
    process_transfers(state, body.getTransfers());

    } catch (BlockProcessingException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in process_operations()");
      throw e;
    }
  }

  // @v0.7.1
  public static void process_proposer_slashings(BeaconState state, List<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.proposer_slashings) <= MAX_PROPOSER_SLASHINGS
      checkArgument(
          proposerSlashings.size() <= MAX_PROPOSER_SLASHINGS,
          "Proposer slashings more than limit in process_proposer_slashings()");

      // For each proposer_slashing in block.body.proposer_slashings:
      for (ProposerSlashing proposer_slashing : proposerSlashings) {
        Validator proposer =
            state
                .getValidator_registry()
                .get(toIntExact(proposer_slashing.getProposer_index().longValue()));

        checkArgument(
            slot_to_epoch(proposer_slashing.getHeader_1().getSlot())
                .equals(slot_to_epoch(proposer_slashing.getHeader_2().getSlot())),
            "Verify that the epoch is the same");

        checkArgument(
            !Objects.equals(
                proposer_slashing.getHeader_1().hash_tree_root(),
                proposer_slashing.getHeader_2().hash_tree_root()),
            "Verify that the headers are different");

        checkArgument(
            is_slashable_validator(proposer, get_current_epoch(state)), "Check proposer is slashable");

        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                proposer_slashing.getHeader_1().signing_root("signature"),
                proposer_slashing.getHeader_1().getSignature(),
                get_domain(
                    state.getFork(),
                    DOMAIN_BEACON_PROPOSER,
                    slot_to_epoch(proposer_slashing.getHeader_1().getSlot())
                    )),
            "Verify signatures are valid 1");

        checkArgument(
            bls_verify(
                proposer.getPubkey(),
                proposer_slashing.getHeader_2().signing_root("signature"),
                proposer_slashing.getHeader_2().getSignature(),
                get_domain(
                    state.getFork(),
                    DOMAIN_BEACON_PROPOSER,
                    slot_to_epoch(proposer_slashing.getHeader_2().getSlot())
                    )),
            "Verify signatures are valid 2");

        slash_validator(state, toIntExact(proposer_slashing.getProposer_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in proposer_slashing()");
      throw new BlockProcessingException(e);
    }
  }

  // @v0.7.1
  public static void process_attester_slashings(BeaconState state, List<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.attester_slashings) <= MAX_ATTESTER_SLASHINGS
      checkArgument(
          attesterSlashings.size() <= MAX_ATTESTER_SLASHINGS,
          "Number of attester slashings more than limit in attester slashing");

      // For each attester_slashing in block.body.attester_slashings:
      for (AttesterSlashing attester_slashing : attesterSlashings) {
        IndexedAttestation attestation_1 =
            attester_slashing.getAttestation_1();
        IndexedAttestation attestation_2 =
            attester_slashing.getAttestation_2();

        checkArgument(is_slashable_attestation_data(attestation_1.getData(), attestation_2.getData()));

        validate_indexed_attestation(state, attestation_1);
        validate_indexed_attestation(state, attestation_2);
        boolean slashed_any = false;

        List<UnsignedLong> attesting_indices_1 = attestation_1.getCustody_bit_0_indices();
        attesting_indices_1.addAll(attestation_1.getCustody_bit_1_indices());
        List<UnsignedLong> attesting_indices_2 = attestation_2.getCustody_bit_0_indices();
        attesting_indices_2.addAll(attestation_1.getCustody_bit_1_indices());

        // retainAll is being used to get the intersection of these two lists
        List<UnsignedLong> sorted_intersection_of_indices = attesting_indices_1;
        sorted_intersection_of_indices.retainAll(attesting_indices_2);
        Collections.sort(sorted_intersection_of_indices);

        for (UnsignedLong index : sorted_intersection_of_indices) {
          if (is_slashable_validator(state.getValidator_registry().get(toIntExact(index.longValue())), get_current_epoch(state)) {
            slash_validator(state, toIntExact(index.intValue()));
            slashed_any = true;
          }
        }

        checkArgument(slashed_any, "No one is slashed");
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in attester_slashing()");
      throw new BlockProcessingException(e);
    }
  }

  // @v0.7.1
  public static void process_attestations(BeaconState state, List<Attestation> attestations)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.attestations) <= MAX_ATTESTATIONS
      checkArgument(
          attestations.size() <= MAX_ATTESTATIONS,
          "Number of attestations more than limit in processAttestations()");

      for (Attestation attestation : attestations) {
        AttestationData data = attestation.getData();
        UnsignedLong attestation_slot = get_attestation_data_slot(state, data);

        checkArgument(
            attestation_slot
                    .plus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY))
                    .compareTo(state.getSlot())
                <= 0,
            "Attestation submitted too quickly");

        checkArgument(
            state
                    .getSlot()
                    .compareTo(attestation_slot.plus(UnsignedLong.valueOf(SLOTS_PER_EPOCH)))
                <= 0,
            "Attestation submitted too far in history");

        PendingAttestation pendingAttestation = new PendingAttestation(
                data,
                attestation.getAggregation_bitfield(),
                state.getSlot().minus(attestation_slot),
                get_beacon_proposer_index(state)
        );

        checkArgument(data.getTarget_epoch().equals(get_previous_epoch(state))
                        || data.getTarget_epoch().equals(get_current_epoch(state)),
                "Verify attestation data target epoch is in either previous or current epoch"
        );
        Triple<UnsignedLong, Bytes32, UnsignedLong> state_ffg_data;
        Crosslink parent_crosslink;
        if (data.getTarget_epoch().equals(get_current_epoch(state))) {
           state_ffg_data = new ImmutableTriple<>(
                  state.getCurrent_justified_epoch(),
                  state.getCurrent_justified_root(),
                  get_current_epoch(state)
          );
          parent_crosslink = state.getCurrent_crosslinks().get(toIntExact(data.getCrosslink().getShard().longValue()));
          state.getCurrent_epoch_attestations().add(pendingAttestation);
        } else {
          state_ffg_data = new ImmutableTriple<>(
                  state.getPrevious_justified_epoch(),
                  state.getPrevious_justified_root(),
                  get_previous_epoch(state)
          );
          parent_crosslink = state.getPrevious_crosslinks().get(toIntExact(data.getCrosslink().getShard().longValue()));
          state.getPrevious_epoch_attestations().add(pendingAttestation);
        }

        Triple<UnsignedLong, Bytes32, UnsignedLong> attestation_ffg_data = new ImmutableTriple<>(
                data.getSource_epoch(),
                data.getSource_root(),
                data.getTarget_epoch()
        );
        checkArgument(state_ffg_data.equals(attestation_ffg_data), "Check FFG data");
        checkArgument(data.getCrosslink().getStart_epoch().equals(parent_crosslink.getEnd_epoch()), "Check crosslink data 1");
        checkArgument(data.getCrosslink().getEnd_epoch().equals(min(data.getTarget_epoch(), parent_crosslink.getEnd_epoch().plus(UnsignedLong.valueOf(MAX_EPOCHS_PER_CROSSLINK)))), "Check crosslink data 2");
        checkArgument(data.getCrosslink().getParent_root().equals(parent_crosslink.hash_tree_root()), "Check crosslink data 3");
        checkArgument(data.getCrosslink().getData_root().equals(ZERO_HASH), "Check crosslink data 4");
        validate_indexed_attestation(state, convert_to_indexed(state, attestation));
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in processAttestations()");
      throw new BlockProcessingException(e);
    }
  }

  /**
   * Helper function for attestations.
   *
   * @param attestation
   * @param state
   * @return true if bitfields and aggregate signature verified. Otherwise, false.
   */
  private static boolean verify_bitfields_and_aggregate_signature(
      Attestation attestation, BeaconState state) throws BlockProcessingException {
    checkArgument(
        Objects.equals(
            attestation.getCustody_bitfield(),
            Bytes.wrap(new byte[attestation.getCustody_bitfield().size()])),
        "checkArgument threw and exception in verify_bitfields_and_aggregate_signature() 1"); // [TO
    // BE
    // REMOVED IN PHASE 1]
    checkArgument(
        !Objects.equals(
            attestation.getAggregation_bitfield(),
            Bytes.wrap(new byte[attestation.getAggregation_bitfield().size()])),
        "checkArgument threw and exception in verify_bitfields_and_aggregate_signature() 2");

    // Get the committee for the specific shard that this attestation is for
    List<List<Integer>> crosslink_committees = new ArrayList<>();
    List<CrosslinkCommittee> crosslink_committees_at_slot =
        get_crosslink_committees_at_slot(state, attestation.getData().getSlot());
    for (CrosslinkCommittee crosslink_committee : crosslink_committees_at_slot) {
      if (Objects.equals(crosslink_committee.getShard(), attestation.getData().getShard())) {
        crosslink_committees.add(crosslink_committee.getCommittee());
      }
    }
    List<Integer> crosslink_committee = crosslink_committees.get(0);

    for (int i = 0; i < crosslink_committee.size(); i++) {
      checkArgument(
          get_bitfield_bit(attestation.getAggregation_bitfield(), i) != 0b0
              || get_bitfield_bit(attestation.getCustody_bitfield(), i) == 0b0,
          "checkArgument threw and exception in verify_bitfields_and_aggregate_signature() 3");
    }

    List<Integer> participants =
        get_attestation_participants(
            state, attestation.getData(), attestation.getAggregation_bitfield());
    List<Integer> custody_bit_1_participants =
        get_attestation_participants(
            state, attestation.getData(), attestation.getCustody_bitfield());
    List<Integer> custody_bit_0_participants = new ArrayList<>();
    for (Integer participant : participants) {
      if (custody_bit_1_participants.indexOf(participant) == -1) {
        custody_bit_0_participants.add(participant);
      }
    }

    List<BLSPublicKey> pubkey0 = new ArrayList<>();
    for (int i = 0; i < custody_bit_0_participants.size(); i++) {
      pubkey0.add(state.getValidator_registry().get(custody_bit_0_participants.get(i)).getPubkey());
    }

    List<BLSPublicKey> pubkey1 = new ArrayList<>();
    for (int i = 0; i < custody_bit_1_participants.size(); i++) {
      pubkey1.add(state.getValidator_registry().get(custody_bit_1_participants.get(i)).getPubkey());
    }

    checkArgument(
        bls_verify_multiple(
            Arrays.asList(bls_aggregate_pubkeys(pubkey0), bls_aggregate_pubkeys(pubkey1)),
            Arrays.asList(
                new AttestationDataAndCustodyBit(attestation.getData(), false).hash_tree_root(),
                new AttestationDataAndCustodyBit(attestation.getData(), true).hash_tree_root()),
            attestation.getAggregate_signature(),
            get_domain(
                state.getFork(),
                slot_to_epoch(attestation.getData().getSlot()),
                DOMAIN_ATTESTATION)),
        "checkArgument threw and exception in verify_bitfields_and_aggregate_signature() 4");

    return true;
  }

  // @v0.7.1
  public static void process_deposits(BeaconState state, List<Deposit> deposits)
         throws BlockProcessingException {
      try {
        // Verify that len(block.body.deposits) <= MAX_DEPOSITS
        checkArgument(
                deposits.size() <= MAX_DEPOSITS,
                "More deposits than the limit in process_deposits()");

        for (Deposit deposit : deposits) {
          checkArgument(
                  verify_merkle_branch(
                          deposit.getData().hash_tree_root(),
                          deposit.getProof(),
                          Constants.DEPOSIT_CONTRACT_TREE_DEPTH,
                          toIntExact(state.getDeposit_index().longValue()),
                          state.getLatest_eth1_data().getDeposit_root()),
                  "Verify the Merkle branch");

          state.setDeposit_index(state.getDeposit_index().plus(UnsignedLong.ONE));

          BLSPublicKey pubkey = deposit.getData().getPubkey();
          UnsignedLong amount = deposit.getData().getAmount();
          List<BLSPublicKey> validator_pubkeys =
                  state.getValidator_registry().stream()
                          .map(Validator::getPubkey)
                          .collect(Collectors.toList());
          if (!validator_pubkeys.contains(pubkey)) {

            // Verify the deposit signature (proof of possession).
            // Invalid signatures are allowed by the deposit contract,
            // and hence included on-chain, but must not be processed.
            // Note: deposits are valid across forks, hence the deposit
            // domain is retrieved directly from `bls_domain`
            boolean proof_is_valid =
                    bls_verify(
                            pubkey,
                            deposit.getData().signing_root("signature"),
                            deposit.getData().getSignature(),
                            bls_domain(DOMAIN_DEPOSIT));
            if (!proof_is_valid) {
              return;
            }

            state.getValidator_registry().add(new Validator(
                    pubkey,
                    deposit.getData().getWithdrawal_credentials(),
                    FAR_FUTURE_EPOCH,
                    FAR_FUTURE_EPOCH,
                    FAR_FUTURE_EPOCH,
                    FAR_FUTURE_EPOCH,
                    false,
                    min(amount.minus(amount.mod(UnsignedLong.valueOf(Constants.EFFECTIVE_BALANCE_INCREMENT))), UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE))));
            state.getBalances().add(amount);
          } else {
            int index = validator_pubkeys.indexOf(pubkey);
            increase_balance(state, index, amount);
          }
        }
      } catch (IllegalArgumentException e) {
        LOG.log(Level.WARN, "BlockProcessingException thrown in processExits()");
        throw new BlockProcessingException(e);
      }
    }

  // @v0.7.1
  public static void process_voluntary_exits(BeaconState state, List<VoluntaryExit> exits)
      throws BlockProcessingException {
    try {
      // Verify that len(block.body.voluntary_exits) <= MAX_VOLUNTARY_EXITS
      checkArgument(
          exits.size() <= Constants.MAX_VOLUNTARY_EXITS,
          "checkArgument threw and exception in processExits()");

      // For each exit in block.body.voluntaryExits:
      for (VoluntaryExit exit : exits) {

        Validator validator =
            state
                .getValidator_registry()
                .get(toIntExact(exit.getValidator_index().longValue()));

        checkArgument(is_active_validator(validator, get_current_epoch(state)),
                "Verify the validator is active");

        checkArgument(validator.getExit_epoch().compareTo(FAR_FUTURE_EPOCH) == 0, "Verify the validator has not yet exited");

        checkArgument(
                get_current_epoch(state).compareTo(exit.getEpoch()) >= 0,
                "Exits must specify an epoch when they become valid; they are not valid before then");

        checkArgument(
            get_current_epoch(state)
                    .compareTo(validator.getActivation_epoch()
                                    .plus(UnsignedLong.valueOf(PERSISTENT_COMMITTEE_PERIOD)))
                    >= 0,
            "Verify the validator has been active long enough");

        int domain = get_domain(state, DOMAIN_VOLUNTARY_EXIT, exit.getEpoch());
        checkArgument(
            bls_verify(
                validator.getPubkey(),
                exit.signing_root("signature"),
                exit.getSignature(),
                domain),
            "Verify signature");

        // - Run initiate_validator_exit(state, exit.validator_index)
        initiate_validator_exit(state, toIntExact(exit.getValidator_index().longValue()));
      }
    } catch (IllegalArgumentException e) {
      LOG.log(Level.WARN, "BlockProcessingException thrown in processExits()");
      throw new BlockProcessingException(e);
    }
  }


  // @v0.7.1
  public static void process_transfers(BeaconState state, List<Transfer> transfers)
      throws BlockProcessingException {
    // Verify that len(block.body.transfers) <= MAX_TRANSFERS and that all transfers are distinct.
    checkArgument(
        transfers.size() <= MAX_TRANSFERS,
        "More transfers in block than the limit");

    for (Transfer transfer : transfers) {
      checkArgument(
          state
                  .getBalances()
                  .get(transfer.getSender().intValue())
                  .compareTo(max(transfer.getAmount(), transfer.getFee()))
              >= 0,
          "Verify the amount and fee aren't individually too big (for anti-overflow purpose)");

      checkArgument(state.getSlot().equals(transfer.getSlot()), "A transfer is valid in only one slot");

      checkArgument(
              state
                  .getValidator_registry()
                  .get(transfer.getSender().intValue())
                  .getActivation_eligibility_epoch()
                  .equals(FAR_FUTURE_EPOCH)
              || get_current_epoch(state)
                      .compareTo(
                          state
                              .getValidator_registry()
                              .get(transfer.getSender().intValue())
                              .getWithdrawable_epoch())
                      >= 0
              || transfer.getAmount()
                      .plus(transfer.getFee())
                      .plus(UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE))
                      .compareTo(state.getBalances()
                              .get(transfer.getSender().intValue()))
                      <= 0,
          "Sender must be not yet eligible for activation, withdrawn, or transfer balance over MAX_EFFECTIVE_BALANCE");

      checkArgument(
          state
              .getValidator_registry()
              .get(transfer.getSender().intValue())
              .getWithdrawal_credentials()
              .equals(
                      Bytes.wrap(int_to_bytes(BLS_WITHDRAWAL_PREFIX_BYTE, 1), Hash.keccak256(transfer.getPubkey().toBytes().slice(1)))),
          "Verify that the pubkey is valid");

      checkArgument(
          bls_verify(
              transfer.getPubkey(),
              transfer.signing_root("signature"),
              transfer.getSignature(),
              get_domain(state, DOMAIN_TRANSFER)),
          "Verify that the signature is valid");


      // Process transfers
      BeaconStateUtil.decrease_balance(state, transfer.getSender().intValue(), transfer.getAmount().plus(transfer.getFee()));
      BeaconStateUtil.increase_balance(state, transfer.getRecipient().intValue(), transfer.getAmount());
      BeaconStateUtil.increase_balance(state, get_beacon_proposer_index(state), transfer.getFee());

      checkArgument(!(UnsignedLong.ZERO.compareTo(state.getBalances().get(transfer.getSender().intValue())) < 0
              && state.getBalances().get(transfer.getSender().intValue()).compareTo(UnsignedLong.valueOf(MIN_DEPOSIT_AMOUNT)) < 0),
              "Verify balances are not dust 1");
      checkArgument(!(UnsignedLong.ZERO.compareTo(state.getBalances().get(transfer.getRecipient().intValue())) < 0
                      && state.getBalances().get(transfer.getSender().intValue()).compareTo(UnsignedLong.valueOf(MIN_DEPOSIT_AMOUNT)) < 0),
              "Verify balances are not dust 2");
    }
  }

  /**
   * @param state
   * @param block
   */
  public static void verify_block_state_root(BeaconState state, BeaconBlock block) {
    if (!block.getState_root().equals(Bytes32.ZERO)) {
      checkArgument(
          block.getState_root().equals(state.hash_tree_root()),
          "State roots don't match in verify_block_state_root");
    }
  }

  private static <T> boolean allDistinct(List<T> list) {
    HashSet<T> set = new HashSet<>();

    for (T t : list) {
      if (set.contains(t)) {
        return false;
      }

      set.add(t);
    }

    return true;
  }
}
