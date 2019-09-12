package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.PubsubPublisherApi;
import io.libp2p.core.pubsub.Topic;
import io.libp2p.pubsub.gossip.Gossip;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class GossipMessageHandler implements Consumer<MessageApi> {
  private static final int MAX_SENT_MESSAGES = 2048;

  private static final Topic blocksTopic = new Topic("/eth2/beacon_block/ssz");
  private static final Topic attestationsTopic = new Topic("/eth2/beacon_attestation/ssz");
  private static EventBus eventBus;
  private final PubsubPublisherApi publisher;
  private final Set<Bytes> sentMessages = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<>() {
    @Override
    protected boolean removeEldestEntry(final Entry<Bytes, Boolean> eldest) {
      return size() > MAX_SENT_MESSAGES;
    }
  }));

  public GossipMessageHandler(final PubsubPublisherApi publisher) {
    this.publisher = publisher;
  }

  public static void init(final Gossip gossip, final PrivKey privateKey, final EventBus eventBus) {
    GossipMessageHandler.eventBus = eventBus;
    final PubsubPublisherApi publisher =
        gossip.createPublisher(privateKey, new Random().nextLong());
    final GossipMessageHandler handler = new GossipMessageHandler(publisher);
    gossip.subscribe(handler, blocksTopic, attestationsTopic);
    eventBus.register(handler);
  }

  @Override
  public void accept(MessageApi msg) {
    if (msg.getTopics().contains(blocksTopic)) {
      BeaconBlock block =
          SimpleOffsetSerializer.deserialize(Bytes.wrapByteBuf(msg.getData()), BeaconBlock.class);
      eventBus.post(block);
    } else if (msg.getTopics().contains(attestationsTopic)) {
      Attestation attestation =
          SimpleOffsetSerializer.deserialize(Bytes.wrapByteBuf(msg.getData()), Attestation.class);
      eventBus.post(attestation);
    }
  }

  @Subscribe
  public void onNewBlock(final BeaconBlock block) {
    gossip(block, blocksTopic);
  }

  @Subscribe
  public void onNewAttestation(final Attestation attestation) {
    gossip(attestation, attestationsTopic);
  }

  private void gossip(final SimpleOffsetSerializable block, final Topic topic) {
    Bytes bytes = SimpleOffsetSerializer.serialize(block);
    if (this.sentMessages.add(bytes)) {
      publisher.publish(Unpooled.wrappedBuffer(bytes.toArrayUnsafe()), topic);
    }
  }
}
