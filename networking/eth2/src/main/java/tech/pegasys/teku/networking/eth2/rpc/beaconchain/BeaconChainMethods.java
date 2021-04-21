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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import static tech.pegasys.teku.util.config.Constants.MAX_BLOCK_BY_RANGE_REQUEST_SIZE;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRootMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.GoodbyeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.PingMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.ForkDigestPayloadContext;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.SingleProtocolEth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.VersionedEth2RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.ssz.type.Bytes4;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconChainMethods {
  private static final String STATUS = "/eth2/beacon_chain/req/status";
  private static final String GOODBYE = "/eth2/beacon_chain/req/goodbye";
  private static final String BEACON_BLOCKS_BY_ROOT =
      "/eth2/beacon_chain/req/beacon_blocks_by_root";
  private static final String BEACON_BLOCKS_BY_RANGE =
      "/eth2/beacon_chain/req/beacon_blocks_by_range";
  private static final String GET_METADATA = "/eth2/beacon_chain/req/metadata";
  private static final String PING = "/eth2/beacon_chain/req/ping";

  private final Eth2RpcMethod<StatusMessage, StatusMessage> status;
  private final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye;
  private final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      beaconBlocksByRoot;
  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      beaconBlocksByRange;
  private final Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata;
  private final Eth2RpcMethod<PingMessage, PingMessage> ping;

  private final Collection<Eth2RpcMethod<?, ?>> allMethods;

  private BeaconChainMethods(
      final Eth2RpcMethod<StatusMessage, StatusMessage> status,
      final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye,
      final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot,
      final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> beaconBlocksByRange,
      final Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata,
      final Eth2RpcMethod<PingMessage, PingMessage> ping) {
    this.status = status;
    this.goodBye = goodBye;
    this.beaconBlocksByRoot = beaconBlocksByRoot;
    this.beaconBlocksByRange = beaconBlocksByRange;
    this.getMetadata = getMetadata;
    this.ping = ping;
    allMethods =
        List.of(status, goodBye, beaconBlocksByRoot, beaconBlocksByRange, getMetadata, ping);
  }

  public static BeaconChainMethods create(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final PeerLookup peerLookup,
      final CombinedChainDataClient combinedChainDataClient,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final RpcEncoding rpcEncoding) {
    return new BeaconChainMethods(
        createStatus(asyncRunner, statusMessageFactory, peerLookup, rpcEncoding),
        createGoodBye(asyncRunner, metricsSystem, peerLookup, rpcEncoding),
        createBeaconBlocksByRoot(spec, asyncRunner, recentChainData, peerLookup, rpcEncoding),
        createBeaconBlocksByRange(
            spec, asyncRunner, combinedChainDataClient, peerLookup, rpcEncoding),
        createMetadata(asyncRunner, metadataMessagesFactory, peerLookup, rpcEncoding),
        createPing(asyncRunner, metadataMessagesFactory, peerLookup, rpcEncoding));
  }

  private static Eth2RpcMethod<StatusMessage, StatusMessage> createStatus(
      final AsyncRunner asyncRunner,
      final StatusMessageFactory statusMessageFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final StatusMessageHandler statusHandler = new StatusMessageHandler(statusMessageFactory);
    final RpcContextEncoder<?, StatusMessage> contextEncoder =
        RpcContextEncoder.noop(StatusMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        STATUS,
        1,
        rpcEncoding,
        StatusMessage.SSZ_SCHEMA,
        true,
        contextEncoder,
        statusHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> createGoodBye(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final GoodbyeMessageHandler goodbyeHandler = new GoodbyeMessageHandler(metricsSystem);
    final RpcContextEncoder<?, GoodbyeMessage> contextEncoder =
        RpcContextEncoder.noop(GoodbyeMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        GOODBYE,
        1,
        rpcEncoding,
        GoodbyeMessage.SSZ_SCHEMA,
        false,
        contextEncoder,
        goodbyeHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRoot(
          final Spec spec,
          final AsyncRunner asyncRunner,
          final RecentChainData recentChainData,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {
    final BeaconBlocksByRootMessageHandler beaconBlocksByRootHandler =
        new BeaconBlocksByRootMessageHandler(recentChainData);
    final SignedBeaconBlockSchema phase0BlockSchema =
        spec.forMilestone(SpecMilestone.PHASE0).getSchemaDefinitions().getSignedBeaconBlockSchema();

    final BeaconBlocksByRootRequestMessageSchema requestType =
        BeaconBlocksByRootRequestMessage.SSZ_SCHEMA;
    final boolean expectResponseToRequest = true;

    final RpcContextEncoder<Bytes, SignedBeaconBlock> noContextEncoder =
        RpcContextEncoder.noop(phase0BlockSchema);
    final SingleProtocolEth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
        v1Method =
            new SingleProtocolEth2RpcMethod<>(
                asyncRunner,
                BEACON_BLOCKS_BY_ROOT,
                1,
                rpcEncoding,
                requestType,
                expectResponseToRequest,
                noContextEncoder,
                beaconBlocksByRootHandler,
                peerLookup);

    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      final RpcContextEncoder<Bytes4, SignedBeaconBlock> forkDigestContextEncoder =
          RpcContextEncoder.forkDigest(
              spec, recentChainData, ForkDigestPayloadContext.SIGNED_BEACONBLOCK);

      final SingleProtocolEth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
          v2Method =
              new SingleProtocolEth2RpcMethod<>(
                  asyncRunner,
                  BEACON_BLOCKS_BY_ROOT,
                  2,
                  rpcEncoding,
                  requestType,
                  expectResponseToRequest,
                  forkDigestContextEncoder,
                  beaconBlocksByRootHandler,
                  peerLookup);

      return VersionedEth2RpcMethod.create(
          rpcEncoding, requestType, expectResponseToRequest, List.of(v2Method, v1Method));
    } else {
      return v1Method;
    }
  }

  private static Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRange(
          final Spec spec,
          final AsyncRunner asyncRunner,
          final CombinedChainDataClient combinedChainDataClient,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {

    final BeaconBlocksByRangeMessageHandler beaconBlocksByRangeHandler =
        new BeaconBlocksByRangeMessageHandler(
            combinedChainDataClient, MAX_BLOCK_BY_RANGE_REQUEST_SIZE);
    final SignedBeaconBlockSchema signedBlockSchema =
        spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema();
    final RpcContextEncoder<?, SignedBeaconBlock> contextEncoder =
        RpcContextEncoder.noop(signedBlockSchema);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        BEACON_BLOCKS_BY_RANGE,
        1,
        rpcEncoding,
        BeaconBlocksByRangeRequestMessage.SSZ_SCHEMA,
        true,
        contextEncoder,
        beaconBlocksByRangeHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<EmptyMessage, MetadataMessage> createMetadata(
      final AsyncRunner asyncRunner,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final MetadataMessageHandler messageHandler =
        new MetadataMessageHandler(metadataMessagesFactory);
    final RpcContextEncoder<?, MetadataMessage> contextEncoder =
        RpcContextEncoder.noop(MetadataMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        GET_METADATA,
        1,
        rpcEncoding,
        EmptyMessage.SSZ_SCHEMA,
        true,
        contextEncoder,
        messageHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<PingMessage, PingMessage> createPing(
      final AsyncRunner asyncRunner,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final PingMessageHandler statusHandler = new PingMessageHandler(metadataMessagesFactory);
    final RpcContextEncoder<?, PingMessage> contextEncoder =
        RpcContextEncoder.noop(PingMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        PING,
        1,
        rpcEncoding,
        PingMessage.SSZ_SCHEMA,
        true,
        contextEncoder,
        statusHandler,
        peerLookup);
  }

  public Collection<RpcMethod<?, ?, ?>> all() {
    return Collections.unmodifiableCollection(allMethods);
  }

  public Eth2RpcMethod<StatusMessage, StatusMessage> status() {
    return status;
  }

  public Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye() {
    return goodBye;
  }

  public Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot() {
    return beaconBlocksByRoot;
  }

  public Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> beaconBlocksByRange() {
    return beaconBlocksByRange;
  }

  public Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata() {
    return getMetadata;
  }

  public Eth2RpcMethod<PingMessage, PingMessage> ping() {
    return ping;
  }
}
