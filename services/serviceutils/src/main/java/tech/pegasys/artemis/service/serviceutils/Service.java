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

package tech.pegasys.artemis.service.serviceutils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Service {
  enum State {
    IDLE,
    RUNNING,
    STOPPED
  }

  private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

  public CompletableFuture<?> start() {
    if (!state.compareAndSet(State.IDLE, State.RUNNING)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Attempt to start an already started service."));
    }
    return doStart();
  }

  protected abstract CompletableFuture<?> doStart();

  public CompletableFuture<?> stop() {
    if (!state.compareAndSet(State.RUNNING, State.STOPPED)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Illegal attempt to stop service in state: " + state.get().name()));
    }

    return doStop();
  }

  protected abstract CompletableFuture<?> doStop();
}
