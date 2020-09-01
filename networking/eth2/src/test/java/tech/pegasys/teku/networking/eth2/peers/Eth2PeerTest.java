package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer.InitialStatusSubscriber;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;

class Eth2PeerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Peer delegate = mock(Peer.class);
  private final BeaconChainMethods rpcMethods = mock(BeaconChainMethods.class);
  private final StatusMessageFactory statusMessageFactory = mock(StatusMessageFactory.class);
  private final MetadataMessagesFactory metadataMessagesFactory =
      mock(MetadataMessagesFactory.class);
  private final PeerChainValidator peerChainValidator = mock(PeerChainValidator.class);
  private final RateTracker blockRateTracker = mock(RateTracker.class);
  private final RateTracker rateTracker = mock(RateTracker.class);

  private final PeerStatus randomPeerStatus = randomPeerStatus();

  private final Eth2Peer peer =
      new Eth2Peer(
          delegate,
          rpcMethods,
          statusMessageFactory,
          metadataMessagesFactory,
          peerChainValidator,
          blockRateTracker,
          rateTracker);

  @Test
  void updateStatus_shouldNotUpdateUntilValidationPasses() {
    final InitialStatusSubscriber initialStatusSubscriber = mock(InitialStatusSubscriber.class);
    peer.subscribeInitialStatus(initialStatusSubscriber);
    final SafeFuture<Boolean> validationResult = new SafeFuture<>();
    when(peerChainValidator.validate(peer, randomPeerStatus)).thenReturn(validationResult);

    peer.updateStatus(randomPeerStatus);

    verify(peerChainValidator).validate(peer, randomPeerStatus);
    assertThat(peer.hasStatus()).isFalse();
    verifyNoInteractions(initialStatusSubscriber);

    validationResult.complete(true);
    assertThat(peer.hasStatus()).isTrue();
    assertThat(peer.getStatus()).isEqualTo(randomPeerStatus);
    verify(initialStatusSubscriber).onInitialStatus(randomPeerStatus);
  }

  @Test
  void updateStatus_shouldNotUpdateStatusWhenValidationFails() {
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.completedFuture(false));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isFalse();
  }

  @Test
  void updateStatus_shouldNotUpdateSubsequentStatusWhenValidationFails() {
    final PeerStatus status2 = randomPeerStatus();
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.completedFuture(true));
    when(peerChainValidator.validate(peer, status2)).thenReturn(SafeFuture.completedFuture(false));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isTrue();

    peer.updateStatus(status2);

    // Status stays as the original peer status
    assertThat(peer.hasStatus()).isTrue();
    assertThat(peer.getStatus()).isEqualTo(randomPeerStatus);
  }

  @Test
  void updateStatus_shouldDisconnectPeerIfStatusValidationCompletesExceptionally() {
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Doh!")));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isFalse();
    verify(delegate).disconnectCleanly(DisconnectReason.UNABLE_TO_VERIFY_NETWORK);
  }

  private PeerStatus randomPeerStatus() {
    return new PeerStatus(
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64());
  }
}
