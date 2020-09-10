package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.AttesterDuty;
import tech.pegasys.teku.api.response.v1.validator.GetAttesterDutiesResponse;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.BeaconValidators;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForBeaconState;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.getMaxAgeForEpoch;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.INDEX;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PAGE_SIZE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TG_V1_VALIDATOR;

public class GetAttesterDuties extends AbstractHandler implements Handler  {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/validator/duties/attester/:epoch";
  private final ValidatorDataProvider validatorDataProvider;
  private final SyncDataProvider syncDataProvider;
  private final ChainDataProvider chainDataProvider;

  public GetAttesterDuties(final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.validatorDataProvider = dataProvider.getValidatorDataProvider();
    this.syncDataProvider = dataProvider.getSyncDataProvider();
    this.chainDataProvider = dataProvider.getChainDataProvider();
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get validator duties",
      tags = {TG_V1_VALIDATOR},
      description = "Requests the beacon node to provide a set of attestation duties, " +
          "which should be performed by validators, for a particular epoch. " +
          "Duties should only need to be checked once per epoch, however a " +
          "chain reorganization (of > MIN_SEED_LOOKAHEAD epochs) could occur, " +
          "resulting in a change of duties. For full safety, " +
          "you should monitor chain reorganizations events.",
      queryParams = {
          @OpenApiParam(name = INDEX, description = "Validator index"),
      },
      responses = {
          @OpenApiResponse(
              status = RES_OK,
              content = @OpenApiContent(from = GetAttesterDutiesResponse.class)),
          @OpenApiResponse(status = RES_BAD_REQUEST),
          @OpenApiResponse(status = RES_INTERNAL_ERROR),
          @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE,
          description = "Beacon node is currently syncing and not serving request on that endpoint")

      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    if (!validatorDataProvider.isStoreAvailable() || syncDataProvider.getSyncStatus().is_syncing) {
      ctx.status(SC_SERVICE_UNAVAILABLE);
      return;
    }

    final Map<String, String> parameters = ctx.pathParamMap();
    try {
      final UInt64 epoch = UInt64.valueOf(parameters.get("epoch"));
      final UInt64 currentEpoch = chainDataProvider.getCurrentEpoch();
      if (currentEpoch.plus(UInt64.ONE).isLessThan(epoch)) {
        ctx.result(jsonProvider.objectToJSON(new BadRequest("Cannot get attester duties for " + epoch.minus(currentEpoch) + " epochs ahead")));
        ctx.status(SC_BAD_REQUEST);
        return;
      }
      final List<Integer> indexes = ListQueryParameterUtils.getParameterAsIntegerList(ctx.queryParamMap(), "index");

      SafeFuture<Optional<List<AttesterDuty>>> future = validatorDataProvider.getAttesterDuties(epoch, indexes);

      handleOptionalResult(ctx, future, this::handleResult, SC_INTERNAL_SERVER_ERROR);

    } catch(NumberFormatException ex) {
      ctx.status(SC_BAD_REQUEST);
      ctx.result(jsonProvider.objectToJSON(new BadRequest("Invalid epoch " + parameters.get("epoch") + " or index specified")));
    } catch(IllegalArgumentException ex) {
      ctx.status(SC_BAD_REQUEST);
      ctx.result(jsonProvider.objectToJSON(new BadRequest(ex.getMessage())));
    }
  }

  private Optional<String> handleResult(Context ctx, final List<AttesterDuty> response)
      throws JsonProcessingException {
    return Optional.of(jsonProvider.objectToJSON(new GetAttesterDutiesResponse(response)));
  }

}
