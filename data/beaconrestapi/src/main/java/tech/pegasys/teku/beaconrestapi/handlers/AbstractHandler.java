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

package tech.pegasys.teku.beaconrestapi.handlers;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.beaconrestapi.ParameterUtils;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static javax.servlet.http.HttpServletResponse.SC_GONE;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForSlot;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID;

public abstract class AbstractHandler implements Handler {

  protected final JsonProvider jsonProvider;

  protected AbstractHandler(final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
  }

  public <T> void processStateEndpointRequest(
          final ChainDataProvider provider,
          final Context ctx,
          final Function<Bytes32, SafeFuture<Optional<T>>> rootHandler,
          final Function<UInt64, SafeFuture<Optional<T>>> slotHandler,
          final AbstractHandler.ResultProcessor<T> resultProcessor) {
    final Map<String, String> pathParams = ctx.pathParamMap();
    provider.requireStoreAvailable();
    String stateIdParam = pathParams.get(PARAM_STATE_ID);
    checkArgument(stateIdParam != null, "State_id argument could not be find.");

    SafeFuture<Optional<T>> future;
    final Optional<Bytes32> maybeRoot = ParameterUtils.getPotentialRoot(stateIdParam);
    if (maybeRoot.isPresent()) {
      future = rootHandler.apply(maybeRoot.get());
      handleOptionalResult(ctx, future, resultProcessor, SC_NOT_FOUND);
    } else {
      final Optional<UInt64> maybeSlot = provider.stateParameterToSlot(stateIdParam);
      if (maybeSlot.isEmpty()) {
        ctx.status(SC_NOT_FOUND);
        return;
      }
      UInt64 slot = maybeSlot.get();
      future = slotHandler.apply(slot);
      ctx.header(Header.CACHE_CONTROL, getMaxAgeForSlot(provider, slot));
      if (provider.isFinalized(slot)) {
        handlePossiblyGoneResult(ctx, future, resultProcessor);
      } else {
        handlePossiblyMissingResult(ctx, future, resultProcessor);
      }
    }
  }

  protected <T> void handlePossiblyMissingResult(
      final Context ctx, SafeFuture<Optional<T>> future) {
    handleOptionalResult(ctx, future, SC_NOT_FOUND);
  }

  protected <T> void handlePossiblyMissingResult(
      final Context ctx, SafeFuture<Optional<T>> future, ResultProcessor<T> resultProcessor) {
    handleOptionalResult(ctx, future, resultProcessor, SC_NOT_FOUND);
  }

  protected <T> void handlePossiblyGoneResult(final Context ctx, SafeFuture<Optional<T>> future) {
    handleOptionalResult(ctx, future, SC_GONE);
  }

  protected <T> void handlePossiblyGoneResult(
      final Context ctx, SafeFuture<Optional<T>> future, ResultProcessor<T> resultProcessor) {
    handleOptionalResult(ctx, future, resultProcessor, SC_GONE);
  }

  protected <T> void handleOptionalResult(
      final Context ctx, SafeFuture<Optional<T>> future, final int missingStatus) {
    handleOptionalResult(
        ctx, future, (context, r) -> Optional.of(jsonProvider.objectToJSON(r)), missingStatus);
  }

  protected <T> void handleOptionalResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultProcessor<T> resultProcessor,
      final T emptyResponse) {
    handleOptionalResult(
        ctx, future, resultProcessor, (__, error) -> SafeFuture.failedFuture(error), emptyResponse);
  }

  protected <T> void handleOptionalResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultProcessor<T> resultProcessor,
      ErrorProcessor errorProcessor,
      final T emptyResponse) {
    ctx.result(
        future
            .thenApplyChecked(
                result -> resultProcessor.process(ctx, result.orElse(emptyResponse)).orElse(null))
            .exceptionallyCompose(error -> errorProcessor.handleError(ctx, error)));
  }

  protected <T> void handleOptionalResult(
      final Context ctx,
      SafeFuture<Optional<T>> future,
      ResultProcessor<T> resultProcessor,
      final int missingStatus) {
    ctx.result(
        future.thenApplyChecked(
            result -> {
              if (result.isPresent()) {
                return resultProcessor.process(ctx, result.get()).orElse(null);
              } else {
                ctx.status(missingStatus);
                return null;
              }
            }));
  }

  @FunctionalInterface
  public interface ResultProcessor<T> {
    // Process result, returning an optional serialized response
    Optional<String> process(Context context, T result) throws Exception;
  }

  @FunctionalInterface
  public interface ErrorProcessor {
    SafeFuture<String> handleError(Context context, Throwable t);
  }
}
