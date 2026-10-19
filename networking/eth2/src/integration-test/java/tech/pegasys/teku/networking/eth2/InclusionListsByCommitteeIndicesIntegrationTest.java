/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import io.libp2p.core.NoSuchRemoteProtocolException;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.inclusionlist.InclusionListManager;
import tech.pegasys.teku.statetransition.util.ShufflingDependentRootUtil;
import tech.pegasys.teku.statetransition.validation.SignedInclusionListValidator;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * Peer 1 requests inclusion lists from peer 2 over {@code inclusion_lists_by_indices}. Peer 2
 * serves them from a real {@link InclusionListManager} backed by its chain.
 *
 * <p>Unlike {@link AbstractRpcMethodIntegrationTest}, both peers are built here so they share a
 * genesis (validator count) and clock: peers on different fork digests won't connect, and the
 * inclusion list slot is after the Heze fork.
 */
public class InclusionListsByCommitteeIndicesIntegrationTest {

  // Enough validators that the inclusion list committee has distinct members to request
  private static final int VALIDATOR_COUNT = 64;
  private static final UInt64 HEZE_FORK_EPOCH = UInt64.valueOf(2);

  // Gloas at genesis, Heze from epoch 2, so the chain has both pre- and post-Heze slots
  private final Spec spec = TestSpecFactory.createMinimalWithHezeForkEpoch(HEZE_FORK_EPOCH);
  private final UInt64 hezeForkSlot = spec.computeStartSlotAtEpoch(HEZE_FORK_EPOCH);
  private final UInt64 inclusionListSlot = hezeForkSlot.plus(1);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();
  private final List<StorageSystem> storageSystems = new ArrayList<>();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
    for (final StorageSystem storageSystem : storageSystems) {
      storageSystem.close();
    }
  }

  @Test
  public void shouldReturnRequestedInclusionListsInCommitteeOrder() throws Exception {
    final RemoteChain remoteChain = createRemoteChain(spec, inclusionListSlot);
    final Eth2Peer peer = connect(spec, remoteChain);

    final List<Integer> positions = remoteChain.distinctMemberPositions(4);
    // Stored out of committee order, so the response order can't just be insertion order
    positions.reversed().forEach(remoteChain::storeInclusionListFor);
    // A list for another dependent root must not be served for this one
    remoteChain.storeInclusionListFor(positions.getFirst(), dataStructureUtil.randomBytes32());

    final List<SignedInclusionList> response =
        requestInclusionLists(peer, inclusionListSlot, remoteChain.dependentRoot(), positions);

    assertThat(response)
        .containsExactlyElementsOf(
            positions.stream().map(remoteChain::storedInclusionListFor).toList());
  }

  @Test
  public void shouldOmitInclusionListsForMissingIndices() throws Exception {
    final RemoteChain remoteChain = createRemoteChain(spec, inclusionListSlot);
    final Eth2Peer peer = connect(spec, remoteChain);

    final List<Integer> positions = remoteChain.distinctMemberPositions(5);
    final List<Integer> storedPositions = List.of(positions.get(1), positions.get(3));
    storedPositions.forEach(remoteChain::storeInclusionListFor);

    final List<SignedInclusionList> response =
        requestInclusionLists(peer, inclusionListSlot, remoteChain.dependentRoot(), positions);

    assertThat(response)
        .containsExactlyElementsOf(
            storedPositions.stream().map(remoteChain::storedInclusionListFor).toList());
    assertThat(peer.isConnected()).isTrue();
  }

  @Test
  public void shouldReturnEmptyResponseForEmptyRequest() throws Exception {
    final RemoteChain remoteChain = createRemoteChain(spec, inclusionListSlot);
    final Eth2Peer peer = connect(spec, remoteChain);
    remoteChain.distinctMemberPositions(2).forEach(remoteChain::storeInclusionListFor);

    final List<SignedInclusionList> response =
        requestInclusionLists(peer, inclusionListSlot, remoteChain.dependentRoot(), List.of());

    assertThat(response).isEmpty();
    assertThat(peer.isConnected()).isTrue();
  }

  @Test
  public void shouldRejectPreHezeSlotAsResourceUnavailable() throws Exception {
    final RemoteChain remoteChain = createRemoteChain(spec, inclusionListSlot);
    final Eth2Peer peer = connect(spec, remoteChain);
    final UInt64 preHezeSlot = hezeForkSlot.minus(1);

    assertThatThrownBy(
            () ->
                requestInclusionLists(
                    peer, preHezeSlot, remoteChain.dependentRoot(), List.of(0, 1)))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOfSatisfying(
            RpcException.class,
            error -> {
              assertThat(error.getResponseCode()).isEqualTo(RpcResponseStatus.RESOURCE_UNAVAILABLE);
              assertThat(error.getErrorMessageString())
                  .isEqualTo("Inclusion lists are not available before the Heze fork");
            });
    assertThat(peer.isConnected()).isTrue();
  }

  @Test
  public void shouldRejectRequestAboveCountLimit() throws Exception {
    // With the default config the committee bitvector can't hold more than
    // MAX_REQUEST_INCLUSION_LIST set bits (both are 16), so lower the limit to exceed it
    final int maxRequestInclusionList = 4;
    final Spec limitedSpec =
        TestSpecFactory.createMinimalHeze(
            builder ->
                builder.hezeBuilder(
                    hezeBuilder -> hezeBuilder.maxRequestInclusionList(maxRequestInclusionList)));
    final RemoteChain remoteChain = createRemoteChain(limitedSpec, UInt64.ONE);
    final Eth2Peer peer = connect(limitedSpec, remoteChain);
    final List<Integer> positions =
        IntStream.range(0, maxRequestInclusionList + 1).boxed().toList();

    assertThatThrownBy(
            () ->
                requestInclusionLists(
                    peer, remoteChain.slot(), remoteChain.dependentRoot(), positions))
        .isInstanceOf(ExecutionException.class)
        .cause()
        .isInstanceOfSatisfying(
            RpcException.class,
            error -> {
              assertThat(error.getResponseCode()).isEqualTo(RpcResponseStatus.INVALID_REQUEST_CODE);
              assertThat(error.getErrorMessageString())
                  .isEqualTo("Only a maximum of 4 inclusion lists can be requested per request");
            });
    assertThat(peer.isConnected()).isTrue();
  }

  @Test
  public void shouldNotSendRequestWhenHezeIsNotScheduledLocally() throws Exception {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    // Both peers stay at genesis so they share the Gloas fork digest and can connect
    final StorageSystem remoteStorage = createStorage(spec);
    final Eth2Peer peer = connect(gloasSpec, spec, remoteStorage, Optional.empty());

    final SafeFuture<Void> result =
        peer.requestInclusionListsByCommitteeIndices(
            inclusionListSlot,
            Bytes32.ZERO,
            committeeIndices(List.of(0)),
            RpcResponseListener.from(__ -> {}));

    assertThatThrownBy(() -> waitFor(result))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("InclusionListsByCommitteeIndices method is not supported");
    assertThat(peer.isConnected()).isTrue();
  }

  @Test
  public void shouldFailRequestWhenHezeIsNotScheduledRemotely() throws Exception {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final StorageSystem remoteStorage = createStorage(gloasSpec);
    final Eth2Peer peer = connect(spec, gloasSpec, remoteStorage, Optional.empty());

    final SafeFuture<Void> result =
        peer.requestInclusionListsByCommitteeIndices(
            inclusionListSlot,
            Bytes32.ZERO,
            committeeIndices(List.of(0)),
            RpcResponseListener.from(__ -> {}));

    // The remote doesn't register the protocol, so libp2p protocol negotiation fails
    assertThatThrownBy(() -> waitFor(result))
        .hasRootCauseInstanceOf(NoSuchRemoteProtocolException.class)
        .hasMessageContaining("/eth2/beacon_chain/req/inclusion_lists_by_indices/1/");
    assertThat(peer.isConnected()).isTrue();
  }

  private List<SignedInclusionList> requestInclusionLists(
      final Eth2Peer peer,
      final UInt64 slot,
      final Bytes32 dependentRoot,
      final List<Integer> committeePositions)
      throws Exception {
    final List<SignedInclusionList> inclusionLists = new ArrayList<>();
    waitFor(
        peer.requestInclusionListsByCommitteeIndices(
            slot,
            dependentRoot,
            committeeIndices(committeePositions),
            RpcResponseListener.from(inclusionLists::add)));
    assertThat(peer.getOutstandingRequests()).isZero();
    return inclusionLists;
  }

  private SszBitvector committeeIndices(final List<Integer> committeePositions) {
    return SszBitvectorSchema.create(
            SpecConfigHeze.required(spec.forMilestone(SpecMilestone.HEZE).getConfig())
                .getInclusionListCommitteeSize())
        .ofBits(committeePositions.stream().mapToInt(Integer::intValue).toArray());
  }

  private Eth2Peer connect(final Spec networkSpec, final RemoteChain remoteChain) throws Exception {
    return connect(
        networkSpec,
        networkSpec,
        remoteChain.storage(),
        Optional.of(remoteChain.inclusionListManager()));
  }

  /** Connects a fresh local node (peer 1) to a node (peer 2) serving from {@code remoteStorage}. */
  private Eth2Peer connect(
      final Spec localSpec,
      final Spec remoteSpec,
      final StorageSystem remoteStorage,
      final Optional<InclusionListManager> remoteInclusionListManager)
      throws Exception {
    final StorageSystem localStorage = createStorage(localSpec);
    // Peers only connect when their clocks put them on the same fork digest
    remoteStorage
        .recentChainData()
        .getCurrentSlot()
        .ifPresent(localStorage.chainUpdater()::setCurrentSlot);

    final Eth2P2PNetworkBuilder remoteNetworkBuilder =
        networkFactory
            .builder()
            .rpcEncoding(
                RpcEncoding.createSszSnappyEncoding(
                    remoteSpec.getNetworkingConfig().getMaxPayloadSize()))
            .recentChainData(remoteStorage.recentChainData())
            .historicalChainData(remoteStorage.chainStorage())
            .spec(remoteSpec);
    remoteInclusionListManager.ifPresent(remoteNetworkBuilder::inclusionListManager);
    final Eth2P2PNetwork remoteNetwork = remoteNetworkBuilder.startNetwork();

    final Eth2P2PNetwork localNetwork =
        networkFactory
            .builder()
            .rpcEncoding(
                RpcEncoding.createSszSnappyEncoding(
                    localSpec.getNetworkingConfig().getMaxPayloadSize()))
            .peer(remoteNetwork)
            .recentChainData(localStorage.recentChainData())
            .historicalChainData(localStorage.chainStorage())
            .spec(localSpec)
            .startNetwork();
    return localNetwork.getPeer(remoteNetwork.getNodeId()).orElseThrow();
  }

  /** Every node uses the same validator count so they share a genesis. */
  private StorageSystem createStorage(final Spec storageSpec) {
    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create()
            .numberOfValidators(VALIDATOR_COUNT)
            .specProvider(storageSpec)
            .build();
    storageSystem.chainUpdater().initializeGenesis();
    storageSystems.add(storageSystem);
    return storageSystem;
  }

  /**
   * Builds peer 2's chain up to {@code slot} and resolves that slot's inclusion list committee the
   * same way the request handler does.
   */
  private RemoteChain createRemoteChain(final Spec remoteSpec, final UInt64 slot) throws Exception {
    final StorageSystem storage = createStorage(remoteSpec);
    final SignedBlockAndState head = storage.chainUpdater().advanceChainUntil(slot);
    storage.chainUpdater().updateBestBlock(head);
    storage.chainUpdater().setCurrentSlot(slot);

    final Bytes32 dependentRoot =
        ShufflingDependentRootUtil.getShufflingDependentRoot(remoteSpec, head.getState(), slot);
    final BeaconState dependentState =
        waitFor(
                storage
                    .recentChainData()
                    .retrieveBlockState(new SlotAndBlockRoot(slot, dependentRoot)))
            .orElseThrow();
    final IntList committee =
        remoteSpec
            .atSlot(slot)
            .getInclusionListUtil()
            .orElseThrow()
            .getInclusionListCommittee(dependentState, slot);

    final InclusionListManager inclusionListManager =
        new InclusionListManager(
            mock(SignedInclusionListValidator.class),
            mock(ForkChoice.class),
            remoteSpec,
            storage.recentChainData(),
            mock(InclusionListStore.class));
    return new RemoteChain(
        remoteSpec, storage, slot, dependentRoot, committee, inclusionListManager);
  }

  private class RemoteChain {
    private final Spec remoteSpec;
    private final StorageSystem storage;
    private final UInt64 slot;
    private final Bytes32 dependentRoot;
    private final IntList committee;
    private final InclusionListManager inclusionListManager;
    private final Map<Integer, SignedInclusionList> storedByPosition = new HashMap<>();

    private RemoteChain(
        final Spec remoteSpec,
        final StorageSystem storage,
        final UInt64 slot,
        final Bytes32 dependentRoot,
        final IntList committee,
        final InclusionListManager inclusionListManager) {
      this.remoteSpec = remoteSpec;
      this.storage = storage;
      this.slot = slot;
      this.dependentRoot = dependentRoot;
      this.committee = committee;
      this.inclusionListManager = inclusionListManager;
    }

    StorageSystem storage() {
      return storage;
    }

    UInt64 slot() {
      return slot;
    }

    Bytes32 dependentRoot() {
      return dependentRoot;
    }

    InclusionListManager inclusionListManager() {
      return inclusionListManager;
    }

    /** Committee positions whose validators are distinct, so each maps to one list. */
    List<Integer> distinctMemberPositions(final int count) {
      final List<Integer> positions = new ArrayList<>();
      final Set<Integer> seenValidators = new HashSet<>();
      for (int position = 0; position < committee.size() && positions.size() < count; position++) {
        if (seenValidators.add(committee.getInt(position))) {
          positions.add(position);
        }
      }
      assertThat(positions).hasSize(count);
      return positions;
    }

    void storeInclusionListFor(final int position) {
      storedByPosition.put(position, storeInclusionListFor(position, dependentRoot));
    }

    SignedInclusionList storeInclusionListFor(final int position, final Bytes32 root) {
      final SchemaDefinitionsHeze schemaDefinitions =
          SchemaDefinitionsHeze.required(remoteSpec.atSlot(slot).getSchemaDefinitions());
      final InclusionList inclusionList =
          schemaDefinitions
              .getInclusionListSchema()
              .create(
                  slot,
                  UInt64.valueOf(committee.getInt(position)),
                  root,
                  List.of(
                      dataStructureUtil.randomExecutionPayloadTransaction(),
                      dataStructureUtil.randomExecutionPayloadTransaction()));
      final SignedInclusionList signedInclusionList =
          schemaDefinitions
              .getSignedInclusionListSchema()
              .create(inclusionList, dataStructureUtil.randomSignature());
      inclusionListManager.add(signedInclusionList);
      return signedInclusionList;
    }

    SignedInclusionList storedInclusionListFor(final int position) {
      return Optional.ofNullable(storedByPosition.get(position)).orElseThrow();
    }
  }
}
