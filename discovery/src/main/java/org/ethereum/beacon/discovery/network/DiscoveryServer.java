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

package org.ethereum.beacon.discovery.network;

import org.ethereum.beacon.schedulers.Scheduler;
import org.reactivestreams.Publisher;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** Discovery server which listens to incoming messages according to setup */
public interface DiscoveryServer {
  void start(Scheduler scheduler);

  void stop();

  /** Raw incoming packets stream */
  Publisher<BytesValue> getIncomingPackets();
}
