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

package tech.pegasys.teku.validator.remote;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;
import java.net.SocketTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class EventSourceHandler implements EventHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final JsonProvider jsonProvider = new JsonProvider();
  private final ValidatorTimingChannel validatorTimingChannel;

  public EventSourceHandler(final ValidatorTimingChannel validatorTimingChannel) {
    this.validatorTimingChannel = validatorTimingChannel;
  }

  @Override
  public void onOpen() {
    VALIDATOR_LOGGER.connectedToBeaconNode();
    // We might have missed some events while connecting or reconnected so ensure the duties are
    // recalculated
    validatorTimingChannel.onPossibleMissedEvents();
  }

  @Override
  public void onClosed() {
    LOG.info("Beacon chain event stream closed");
  }

  @Override
  public void onMessage(final String event, final MessageEvent messageEvent) throws Exception {
    try {
      switch (EventType.valueOf(event)) {
        case head:
          handleHeadEvent(messageEvent.getData());
          return;
        case chain_reorg:
          handleChainReorgEvent(messageEvent);
          return;
        default:
          LOG.warn("Received unexpected event type: " + event);
      }
    } catch (final IllegalArgumentException | JsonProcessingException e) {
      LOG.warn(
          "Received invalid event from beacon node. Event type: {} Event data: {}",
          event,
          messageEvent.getData(),
          e);
    }
  }

  private void handleHeadEvent(final String data) throws JsonProcessingException {
    final HeadEvent headEvent = jsonProvider.jsonToObject(data, HeadEvent.class);
    validatorTimingChannel.onHeadUpdate(
        headEvent.slot, headEvent.block, headEvent.currentTargetRoot, headEvent.previousTargetRoot);
    validatorTimingChannel.onAttestationCreationDue(headEvent.slot);
  }

  private void handleChainReorgEvent(final MessageEvent messageEvent)
      throws JsonProcessingException {
    final ChainReorgEvent reorgEvent =
        jsonProvider.jsonToObject(messageEvent.getData(), ChainReorgEvent.class);
    final UInt64 commonAncestorSlot;
    if (reorgEvent.depth.isGreaterThan(reorgEvent.slot)) {
      LOG.warn("Received reorg that is deeper than the current chain");
      commonAncestorSlot = UInt64.ZERO;
    } else {
      commonAncestorSlot = reorgEvent.slot.minus(reorgEvent.depth);
    }
    validatorTimingChannel.onChainReorg(reorgEvent.slot, commonAncestorSlot);
  }

  @Override
  public void onComment(final String comment) {}

  @Override
  public void onError(final Throwable t) {
    if (Throwables.getRootCause(t) instanceof SocketTimeoutException) {
      LOG.info(
          "Timed out waiting for events from beacon node event stream. Reconnecting. This is normal if the beacon node is still syncing.");
    } else {
      VALIDATOR_LOGGER.beaconNodeConnectionError(t);
    }
  }
}
