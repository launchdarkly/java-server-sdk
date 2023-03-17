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
import com.launchdarkly.sdk.server.integrations.ApplicationInfoBuilder;
import com.launchdarkly.sdk.server.integrations.BigSegmentsConfigurationBuilder;
import com.launchdarkly.sdk.server.integrations.EventProcessorBuilder;
import com.launchdarkly.sdk.server.integrations.ServiceEndpointsBuilder;
import com.launchdarkly.sdk.server.integrations.StreamingDataSourceBuilder;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        EvaluationDetail<Boolean> boolResult = params.user == null ?
            client.boolVariationDetail(params.flagKey, params.context, params.defaultValue.booleanValue()) :
              client.boolVariationDetail(params.flagKey, params.user, params.defaultValue.booleanValue());
        resp.value = LDValue.of(boolResult.getValue());
        genericResult = boolResult;
        break;
      case "int":
        EvaluationDetail<Integer> intResult = params.user == null ?
            client.intVariationDetail(params.flagKey, params.context, params.defaultValue.intValue()) :
              client.intVariationDetail(params.flagKey, params.user, params.defaultValue.intValue());
        resp.value = LDValue.of(intResult.getValue());
        genericResult = intResult;
        break;
      case "double":
        EvaluationDetail<Double> doubleResult = params.user == null ?
            client.doubleVariationDetail(params.flagKey, params.context, params.defaultValue.doubleValue()) :
              client.doubleVariationDetail(params.flagKey, params.user, params.defaultValue.doubleValue());
        resp.value = LDValue.of(doubleResult.getValue());
        genericResult = doubleResult;
        break;
      case "string":
        EvaluationDetail<String> stringResult = params.user == null ?
            client.stringVariationDetail(params.flagKey, params.context, params.defaultValue.stringValue()) :
              client.stringVariationDetail(params.flagKey, params.user, params.defaultValue.stringValue());
        resp.value = LDValue.of(stringResult.getValue());
        genericResult = stringResult;
        break;
      default:
        EvaluationDetail<LDValue> anyResult = params.user == null ?
            client.jsonValueVariationDetail(params.flagKey, params.context, params.defaultValue) :
              client.jsonValueVariationDetail(params.flagKey, params.user, params.defaultValue);
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
        resp.value = LDValue.of(params.user == null ?
            client.boolVariation(params.flagKey, params.context, params.defaultValue.booleanValue()) :
              client.boolVariation(params.flagKey, params.user, params.defaultValue.booleanValue()));
        break;
      case "int":
        resp.value = LDValue.of(params.user == null ?
            client.intVariation(params.flagKey, params.context, params.defaultValue.intValue()) :
              client.intVariation(params.flagKey, params.user, params.defaultValue.intValue()));
        break;
      case "double":
        resp.value = LDValue.of(params.user == null ?
            client.doubleVariation(params.flagKey, params.context, params.defaultValue.doubleValue()) :
              client.doubleVariation(params.flagKey, params.user, params.defaultValue.doubleValue()));
        break;
      case "string":
        resp.value = LDValue.of(params.user == null ?
            client.stringVariation(params.flagKey, params.context, params.defaultValue.stringValue()) :
              client.stringVariation(params.flagKey, params.user, params.defaultValue.stringValue()));
        break;
      default:
        resp.value = params.user == null ?
            client.jsonValueVariation(params.flagKey, params.context, params.defaultValue) :
              client.jsonValueVariation(params.flagKey, params.user, params.defaultValue);
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
    FeatureFlagsState state;
    if (params.user == null) {
      state = client.allFlagsState(params.context, options.toArray(new FlagsStateOption[0]));
    } else {
      state = client.allFlagsState(params.user, options.toArray(new FlagsStateOption[0]));
    }
    EvaluateAllFlagsResponse resp = new EvaluateAllFlagsResponse();
    resp.state = LDValue.parse(JsonSerialization.serialize(state));
    return resp;
  }
  
  private void doIdentifyEvent(IdentifyEventParams params) {
    if (params.user == null) {
      client.identify(params.context);
    } else {
      client.identify(params.user);
    }
  }
  
  private void doCustomEvent(CustomEventParams params) {
    if ((params.data == null || params.data.isNull()) && params.omitNullData && params.metricValue == null) {
      if (params.user == null) {
        client.track(params.eventKey, params.context);
      } else {
        client.track(params.eventKey, params.user);
      }
    } else if (params.metricValue == null) {
      if (params.user == null) {
        client.trackData(params.eventKey, params.context, params.data);
      } else {
        client.trackData(params.eventKey, params.user, params.data);
      }
    } else {
      if (params.user == null) {
        client.trackMetric(params.eventKey, params.context, params.data, params.metricValue.doubleValue());
      } else {
        client.trackMetric(params.eventKey, params.user, params.data, params.metricValue.doubleValue());
      }
    }
  }
  
  private ContextBuildResponse doContextBuild(ContextBuildParams params) {
    LDContext c;
    if (params.multi == null) {
      c = doContextBuildSingle(params.single);
    } else {
      ContextMultiBuilder b = LDContext.multiBuilder();
      for (ContextBuildSingleParams s: params.multi) {
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
      for (Map.Entry<String, LDValue> kv: params.custom.entrySet()) {
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
    resp.result = params.user == null ? client.secureModeHash(params.context) : client.secureModeHash(params.user);
    return resp;
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
          new BigSegmentStoreFixture(new CallbackService(params.bigSegments.callbackUri)));
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

    return builder.build();
  }
}
