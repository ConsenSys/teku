/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers;

import java.util.concurrent.atomic.AtomicLong;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

/**
 * Limits the total uncompressed size of the gossip messages for a topic that are waiting to be
 * processed or being processed.
 *
 * <p>Messages wait for the shared P2P async runner, whose queue is only bounded by its number of
 * tasks, so without a limit a burst of large messages can exhaust the heap while they wait.
 * Messages are weighed by their uncompressed size because that is roughly what each one holds once
 * it is decoded.
 */
public class GossipInFlightLimiter {
  private final long maxInFlightBytes;
  private final Counter discardedCounter;
  private final AtomicLong inFlightBytes = new AtomicLong();

  public GossipInFlightLimiter(
      final MetricsSystem metricsSystem, final String topicName, final long maxInFlightBytes) {
    if (maxInFlightBytes <= 0) {
      throw new IllegalArgumentException("Max in-flight bytes must be positive");
    }
    this.maxInFlightBytes = maxInFlightBytes;
    this.discardedCounter =
        metricsSystem
            .createLabelledCounter(
                TekuMetricCategory.NETWORK,
                "gossip_messages_in_flight_limit_discarded_total",
                "Total number of gossip messages discarded because the messages for the topic"
                    + " already being processed were too large in total",
                "topic")
            .labels(topicName);
  }

  /**
   * Admits a message if it fits within the limit.
   *
   * @param messageBytes the uncompressed size of the message
   * @return true if the message was admitted, in which case {@link #release(long)} must be called
   *     with the same size once it is processed
   */
  public boolean tryAcquire(final long messageBytes) {
    while (true) {
      final long current = inFlightBytes.get();
      if (current + messageBytes > maxInFlightBytes) {
        discardedCounter.inc();
        return false;
      }
      if (inFlightBytes.compareAndSet(current, current + messageBytes)) {
        return true;
      }
    }
  }

  public void release(final long messageBytes) {
    inFlightBytes.addAndGet(-messageBytes);
  }

  public long getInFlightBytes() {
    return inFlightBytes.get();
  }

  public long getMaxInFlightBytes() {
    return maxInFlightBytes;
  }
}
