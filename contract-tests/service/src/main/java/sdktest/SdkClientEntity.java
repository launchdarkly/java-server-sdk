package sdktest;

import com.launchdarkly.sdk.ContextBuilder;
import com.launchdarkly.sdk.ContextMultiBuilder;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.json.JsonSerialization;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.FeatureFlagsState;
import com.launchdarkly.sdk.server.FlagsStateOption;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.migrations.Migration;
import com.launchdarkly.sdk.server.migrations.MigrationBuilder;
import com.launchdarkly.sdk.server.migrations.MigrationExecution;
import com.launchdarkly.sdk.server.migrations.MigrationMethodResult;
import com.launchdarkly.sdk.server.migrations.MigrationSerialOrder;
import com.launchdarkly.sdk.server.MigrationStage;
import com.launchdarkly.sdk.server.MigrationVariation;
import com.launchdarkly.sdk.server.integrations.Hook;
import com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder;
import com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.integrations.HooksConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder;
import com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sdktest.Representations.CommandParams;
import sdktest.Representations.ContextBuildParams;
import sdktest.Representations.ContextBuildResponse;
import sdktest.Representations.ContextBuildSingleParams;
import sdktest.Representations.ContextConvertParams;
import sdktest.Representations.CreateInstanceParams;
import sdktest.Representations.CustomEventParams;
import sdktest.Representations.EvaluateAllFlagsParams;
import sdktest.Representations.EvaluateAllFlagsResponse;
import sdktest.Representations.EvaluateFlagParams;
import sdktest.Representations.EvaluateFlagResponse;
import sdktest.Representations.GetBigSegmentsStoreStatusResponse;
import sdktest.Representations.IdentifyEventParams;
import sdktest.Representations.HookConfig;
import sdktest.Representations.SdkConfigHookParams;
import sdktest.Representations.SdkConfigParams;
import sdktest.Representations.SecureModeHashParams;
import sdktest.Representations.SecureModeHashResponse;

public class SdkClientEntity {
  private final LDClient client;
  final Logger logger;

  public SdkClientEntity(TestService owner, CreateInstanceParams params) {
    this.logger = LoggerFactory.getLogger(params.tag);
    logger.info("Starting SDK client");

    LDConfig config = buildSdkConfig(params.configuration, params.tag);
    this.client = new LDClient(params.configuration.credential, config);
    if (!client.isInitialized() && !params.configuration.initCanFail) {
      throw new RuntimeException("client initialization failed or timed out");
    }
  }

  public Object doCommand(CommandParams params) throws TestService.BadRequestException {
    logger.info("Test harness sent command: {}", TestService.gson.toJson(params));
    switch (params.command) {
    case "evaluate":
      return doEvaluateFlag(params.evaluate);
    case "evaluateAll":
      return doEvaluateAll(params.evaluateAll);
    case "identifyEvent":
      doIdentifyEvent(params.identifyEvent);
      return null;
    case "customEvent":
      doCustomEvent(params.customEvent);
      return null;
    case "flushEvents":
      client.flush();
      return null;
    case "getBigSegmentStoreStatus":
      BigSegmentStoreStatusProvider.Status status = client.getBigSegmentStoreStatusProvider().getStatus();
      GetBigSegmentsStoreStatusResponse resp = new GetBigSegmentsStoreStatusResponse();
      resp.available = status.isAvailable();
      resp.stale = status.isStale();
      return resp;
    case "contextBuild":
      return doContextBuild(params.contextBuild);
    case "contextConvert":
      return doContextConvert(params.contextConvert);
    case "secureModeHash":
      return doSecureModeHash(params.secureModeHash);
    case "migrationVariation":
      Representations.MigrationVariationParams mvp = params.migrationVariation;
      if(!MigrationStage.isStage(mvp.defaultStage)) {
        logger.error("The default state for a migration variation was not valid. Received: {}", mvp.defaultStage);
      }
      MigrationVariation variation = client.migrationVariation(mvp.key, mvp.context,
        MigrationStage.of(mvp.defaultStage, MigrationStage.OFF));
      Representations.MigrationVariationResponse res = new Representations.MigrationVariationResponse();
      res.result = variation.getStage().toString();
      return res;
    case "migrationOperation":
      return doMigrationOperation(params.migrationOperation);
    default:
      throw new TestService.BadRequestException("unknown command: " + params.command);
    }
  }

  private EvaluateFlagResponse doEvaluateFlag(EvaluateFlagParams params) {
    EvaluateFlagResponse resp = new EvaluateFlagResponse();
    if (params.detail) {
      EvaluationDetail<?> genericResult;
      switch (params.valueType) {
      case "bool":
        EvaluationDetail<Boolean> boolResult =
          client.boolVariationDetail(params.flagKey, params.context, params.defaultValue.booleanValue());
        resp.value = LDValue.of(boolResult.getValue());
        genericResult = boolResult;
        break;
      case "int":
        EvaluationDetail<Integer> intResult =
              client.intVariationDetail(params.flagKey, params.context, params.defaultValue.intValue());
        resp.value = LDValue.of(intResult.getValue());
        genericResult = intResult;
        break;
      case "double":
        EvaluationDetail<Double> doubleResult =
          client.doubleVariationDetail(params.flagKey, params.context, params.defaultValue.doubleValue());
        resp.value = LDValue.of(doubleResult.getValue());
        genericResult = doubleResult;
        break;
      case "string":
        EvaluationDetail<String> stringResult =
            client.stringVariationDetail(params.flagKey, params.context, params.defaultValue.stringValue());
        resp.value = LDValue.of(stringResult.getValue());
        genericResult = stringResult;
        break;
      default:
        EvaluationDetail<LDValue> anyResult =
            client.jsonValueVariationDetail(params.flagKey, params.context, params.defaultValue);
        resp.value = anyResult.getValue();
        genericResult = anyResult;
        break;
      }
      resp.variationIndex = genericResult.getVariationIndex() == EvaluationDetail.NO_VARIATION ?
            null : Integer.valueOf(genericResult.getVariationIndex());
      resp.reason = genericResult.getReason();
    } else {
      switch (params.valueType) {
      case "bool":
        resp.value = LDValue.of(
            client.boolVariation(params.flagKey, params.context, params.defaultValue.booleanValue()));
        break;
      case "int":
        resp.value = LDValue.of(
            client.intVariation(params.flagKey, params.context, params.defaultValue.intValue()));
        break;
      case "double":
        resp.value = LDValue.of(
            client.doubleVariation(params.flagKey, params.context, params.defaultValue.doubleValue()));
        break;
      case "string":
        resp.value = LDValue.of(
            client.stringVariation(params.flagKey, params.context, params.defaultValue.stringValue()));
        break;
      default:
        resp.value =
            client.jsonValueVariation(params.flagKey, params.context, params.defaultValue);
        break;
      }
    }
    return resp;
  }

  private EvaluateAllFlagsResponse doEvaluateAll(EvaluateAllFlagsParams params) {
    List<FlagsStateOption> options = new ArrayList<>();
    if (params.clientSideOnly) {
      options.add(FlagsStateOption.CLIENT_SIDE_ONLY);
    }
    if (params.detailsOnlyForTrackedFlags) {
      options.add(FlagsStateOption.DETAILS_ONLY_FOR_TRACKED_FLAGS);
    }
    if (params.withReasons) {
      options.add(FlagsStateOption.WITH_REASONS);
    }
    FeatureFlagsState state = client.allFlagsState(params.context, options.toArray(new FlagsStateOption[0]));
    EvaluateAllFlagsResponse resp = new EvaluateAllFlagsResponse();
    resp.state = LDValue.parse(JsonSerialization.serialize(state));
    return resp;
  }

  private void doIdentifyEvent(IdentifyEventParams params) {
      client.identify(params.context);
  }

  private void doCustomEvent(CustomEventParams params) {
    if ((params.data == null || params.data.isNull()) && params.omitNullData && params.metricValue == null) {
      client.track(params.eventKey, params.context);
    } else if (params.metricValue == null) {
      client.trackData(params.eventKey, params.context, params.data);
    } else {
      client.trackMetric(params.eventKey, params.context, params.data, params.metricValue.doubleValue());
    }
  }

  private ContextBuildResponse doContextBuild(ContextBuildParams params) {
    LDContext c;
    if (params.multi == null) {
      c = doContextBuildSingle(params.single);
    } else {
      ContextMultiBuilder b = LDContext.multiBuilder();
      for (ContextBuildSingleParams s : params.multi) {
        b.add(doContextBuildSingle(s));
      }
      c = b.build();
    }
    ContextBuildResponse resp = new ContextBuildResponse();
    if (c.isValid()) {
      resp.output = JsonSerialization.serialize(c);
    } else {
      resp.error = c.getError();
    }
    return resp;
  }

  private LDContext doContextBuildSingle(ContextBuildSingleParams params) {
    ContextBuilder b = LDContext.builder(params.key)
        .kind(params.kind)
        .name(params.name);
    if (params.anonymous != null) {
      b.anonymous(params.anonymous.booleanValue());
    }
    if (params.custom != null) {
      for (Map.Entry<String, LDValue> kv : params.custom.entrySet()) {
        b.set(kv.getKey(), kv.getValue());
      }
    }
    if (params.privateAttrs != null) {
      b.privateAttributes(params.privateAttrs);
    }
    return b.build();
  }

  private ContextBuildResponse doContextConvert(ContextConvertParams params) {
    ContextBuildResponse resp = new ContextBuildResponse();
    try {
      LDContext c = JsonSerialization.deserialize(params.input, LDContext.class);
      resp.output = JsonSerialization.serialize(c);
    } catch (Exception e) {
      resp.error = e.getMessage();
    }
    return resp;
  }

  private SecureModeHashResponse doSecureModeHash(SecureModeHashParams params) {
    SecureModeHashResponse resp = new SecureModeHashResponse();
    resp.result = client.secureModeHash(params.context);
    return resp;
  }

  private Representations.MigrationOperationResponse doMigrationOperation(Representations.MigrationOperationParams params) {
    MigrationCallbackService oldService;
    MigrationCallbackService newService;
    try {
      oldService = new MigrationCallbackService(new URL(params.oldEndpoint));
      newService = new MigrationCallbackService(new URL(params.newEndpoint));
    } catch (Exception e) {
      return null;
    }
    MigrationBuilder<String, String, String, String> migrationBuilder = new MigrationBuilder<>(client);
    migrationBuilder.readExecution(getExecution(params.readExecutionOrder));
    migrationBuilder.trackErrors(params.trackErrors);
    migrationBuilder.trackLatency(params.trackLatency);
    migrationBuilder.write(
      (payload) -> getMigrationMethodResult(payload, oldService),
      payload -> getMigrationMethodResult(payload, newService));
    if (params.trackConsistency) {
      migrationBuilder.read(
        (payload -> getMigrationMethodResult(payload, oldService)),
        (payload) -> getMigrationMethodResult(payload, newService),
        (a, b) -> a.equals(b));
    } else {
      migrationBuilder.read((payload -> getMigrationMethodResult(payload, oldService)),
        (payload) -> getMigrationMethodResult(payload, newService));
    }
    Optional<Migration<String, String, String, String>> opt = migrationBuilder.build();
    if (!opt.isPresent()) {
      return null;
    }
    Migration<String, String, String, String> migration = opt.get();

    switch (params.operation) {
      case "read": {
        Migration.MigrationResult<String> res = migration.read(
          params.key,
          params.context,
          MigrationStage.of(params.defaultStage, MigrationStage.OFF),
          params.payload);
        Representations.MigrationOperationResponse response = new Representations.MigrationOperationResponse();
        if (res.isSuccess()) {
          response.result = res.getResult().orElse(null);
        } else {
          response.error = res.getException().map(ex -> ex.getMessage()).orElse(null);
        }
        return response;
      }
      case "write": {
        Migration.MigrationWriteResult<String> res = migration.write(
          params.key,
          params.context,
          MigrationStage.of(params.defaultStage, MigrationStage.OFF),
          params.payload);
        Representations.MigrationOperationResponse response = new Representations.MigrationOperationResponse();
        if (res.getAuthoritative().isSuccess()) {
          response.result = res.getAuthoritative().getResult().orElse(null);
        } else {
          response.error = res.getAuthoritative()
            .getException().map(ex -> ex.getMessage()).orElse(null);
        }
        return response;
      }
      default:
        return null;
    }
  }

  @NotNull
  private static MigrationMethodResult<String> getMigrationMethodResult(String payload, MigrationCallbackService oldService) {
      String response = oldService.post(payload);
      return MigrationMethodResult.Success(response);
  }

  private MigrationExecution getExecution(String execution) {
    switch (execution) {
      case "serial":
        return MigrationExecution.Serial(MigrationSerialOrder.FIXED);
      case "random":
        return MigrationExecution.Serial(MigrationSerialOrder.RANDOM);
      case "concurrent":
        return MigrationExecution.Parallel();
      default:
        throw new RuntimeException("Invalid execution mode");
    }
  }

  public void close() {
    try {
      client.close();
    } catch (Exception e) {
      logger.error("Unexpected error from LDClient.close(): {}", e);
    }
    logger.info("Test ended");
  }

  private LDConfig buildSdkConfig(SdkConfigParams params, String tag) {
    LDConfig.Builder builder = new LDConfig.Builder();

    builder.logging(Components.logging().baseLoggerName(tag + ".sdk"));

    if (params.startWaitTimeMs != null) {
      builder.startWait(Duration.ofMillis(params.startWaitTimeMs.longValue()));
    }

    ServiceEndpointsBuilder endpoints = Components.serviceEndpoints();

    if (params.streaming != null) {
      endpoints.streaming(params.streaming.baseUri);
      StreamingDataSourceBuilder dataSource = Components.streamingDataSource();
      if (params.streaming.initialRetryDelayMs > 0) {
        dataSource.initialReconnectDelay(Duration.ofMillis(params.streaming.initialRetryDelayMs));
      }
      dataSource.payloadFilter(params.streaming.filter);
      builder.dataSource(dataSource);
    }

    if (params.events == null) {
      builder.events(Components.noEvents());
    } else {
      endpoints.events(params.events.baseUri);
      EventProcessorBuilder eb = Components.sendEvents()
          .allAttributesPrivate(params.events.allAttributesPrivate);
      if (params.events.capacity > 0) {
        eb.capacity(params.events.capacity);
      }
      if (params.events.flushIntervalMs != null) {
        eb.flushInterval(Duration.ofMillis(params.events.flushIntervalMs.longValue()));
      }
      if (params.events.globalPrivateAttributes != null) {
        eb.privateAttributes(params.events.globalPrivateAttributes);
      }
      builder.events(eb);
      builder.diagnosticOptOut(!params.events.enableDiagnostics);
    }

    if (params.bigSegments != null) {
      BigSegmentsConfigurationBuilder bsb = Components.bigSegments(
          new BigSegmentStoreFixture(new BigSegmentCallbackService(params.bigSegments.callbackUri)));
      if (params.bigSegments.staleAfterMs != null) {
        bsb.staleAfter(Duration.ofMillis(params.bigSegments.staleAfterMs));
      }
      if (params.bigSegments.statusPollIntervalMs != null) {
        bsb.statusPollInterval(Duration.ofMillis(params.bigSegments.statusPollIntervalMs));
      }
      if (params.bigSegments.userCacheSize != null) {
        bsb.userCacheSize(params.bigSegments.userCacheSize);
      }
      if (params.bigSegments.userCacheTimeMs != null) {
        bsb.userCacheTime(Duration.ofMillis(params.bigSegments.userCacheTimeMs));
      }
      builder.bigSegments(bsb);
    }

    if (params.tags != null) {
      ApplicationInfoBuilder ab = Components.applicationInfo();
      if (params.tags.applicationId != null) {
        ab.applicationId(params.tags.applicationId);
      }
      if (params.tags.applicationVersion != null) {
        ab.applicationVersion(params.tags.applicationVersion);
      }
      builder.applicationInfo(ab);
    }

    if (params.serviceEndpoints != null) {
      if (params.serviceEndpoints.streaming != null) {
        endpoints.streaming(params.serviceEndpoints.streaming);
      }
      if (params.serviceEndpoints.polling != null) {
        endpoints.polling(params.serviceEndpoints.polling);
      }
      if (params.serviceEndpoints.events != null) {
        endpoints.events(params.serviceEndpoints.events);
      }
    }
    builder.serviceEndpoints(endpoints);

    if (params.hooks != null && params.hooks.hooks != null) {
      List<Hook> hookList = new ArrayList<>();
      for (HookConfig hookConfig : params.hooks.hooks) {

        HookCallbackService callbackService = new HookCallbackService(hookConfig.callbackUri);
        TestHook testHook = new TestHook(
            hookConfig.name,
            callbackService,
            hookConfig.data != null ? hookConfig.data.beforeEvaluation : Collections.emptyMap(),
            hookConfig.data != null ? hookConfig.data.afterEvaluation : Collections.emptyMap(),
            hookConfig.errors != null ? hookConfig.errors.beforeEvaluation : null,
            hookConfig.errors != null ? hookConfig.errors.afterEvaluation : null
        );
        hookList.add(testHook);
      }
      builder.hooks(Components.hooks().setHooks(hookList));
    }

    return builder.build();
  }
}
