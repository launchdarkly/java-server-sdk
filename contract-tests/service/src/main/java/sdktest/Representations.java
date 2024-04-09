package sdktest;

import com.google.gson.annotations.SerializedName;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;

import java.net.URI;
import java.util.List;
import java.util.Map;

public abstract class Representations {
  public static class Status {
    String name;
    String[] capabilities;
    String clientVersion;
  }

  public static class CreateInstanceParams {
    SdkConfigParams configuration;
    String tag; 
  }
  
  public static class SdkConfigParams {
    String credential;
    Long startWaitTimeMs;
    boolean initCanFail;
    SdkConfigStreamParams streaming;
    SdkConfigEventParams events;
    SdkConfigBigSegmentsParams bigSegments;
    SdkConfigTagParams tags;
    SdkConfigServiceEndpointParams serviceEndpoints;
    SdkConfigHookParams hooks;
  }
  
  public static class SdkConfigStreamParams {
    URI baseUri;
    long initialRetryDelayMs;
    String filter;
  }
  
  public static class SdkConfigEventParams {
    URI baseUri;
    boolean allAttributesPrivate;
    int capacity;
    boolean enableDiagnostics;
    String[] globalPrivateAttributes;
    Long flushIntervalMs;
  }
  
  public static class SdkConfigBigSegmentsParams {
    URI callbackUri;
    Integer userCacheSize;
    Long userCacheTimeMs;
    Long statusPollIntervalMs;
    Long staleAfterMs;
  }

  public static class SdkConfigTagParams {
    String applicationId;
    String applicationVersion;
  }

  public static class SdkConfigServiceEndpointParams {
    String streaming;
    String polling;
    String events;
  }

  public static class SdkConfigHookParams {
    List<HookConfig> hooks;
  }

  public static class HookConfig {
    String name;
    URI callbackUri;
    HookData data;
    HookErrors errors;
  }

  public static class HookData {
    Map<String, Object> beforeEvaluation;
    Map<String, Object> afterEvaluation;
  }

  public static class HookErrors {
    String beforeEvaluation;
    String afterEvaluation;
  }

  public static class CommandParams {
    String command;
    EvaluateFlagParams evaluate;
    EvaluateAllFlagsParams evaluateAll;
    IdentifyEventParams identifyEvent;
    CustomEventParams customEvent;
    ContextBuildParams contextBuild;
    ContextConvertParams contextConvert;
    SecureModeHashParams secureModeHash;

    MigrationVariationParams migrationVariation;

    MigrationOperationParams migrationOperation;
  }

  public static class EvaluateFlagParams {
    String flagKey;
    LDContext context;
    String valueType;
    LDValue value;
    LDValue defaultValue;
    boolean detail;
  }

  public static class EvaluateFlagResponse {
    LDValue value;
    Integer variationIndex;
    EvaluationReason reason;
  }

  public static class EvaluateAllFlagsParams {
    LDContext context;
    boolean clientSideOnly;
    boolean detailsOnlyForTrackedFlags;
    boolean withReasons;
  }

  public static class EvaluateAllFlagsResponse {
    LDValue state;
  }

  public static class EvaluationHookCallbackParams {
    EvaluationSeriesContextParam evaluationSeriesContext;
    Map<String, Object> evaluationSeriesData;
    EvaluationDetail<LDValue> evaluationDetail;
    String stage;
  }

  public static class EvaluationSeriesContextParam {
    String flagKey;
    LDContext context;
    LDValue defaultValue;
    String method;
  }

  public static class IdentifyEventParams {
    LDContext context;
  }

  public static class CustomEventParams {
    String eventKey;
    LDContext context;
    LDValue data;
    boolean omitNullData;
    Double metricValue;
  }
  
  public static class GetBigSegmentsStoreStatusResponse {
    boolean available;
    boolean stale;
  }

  public static class ContextBuildParams {
    ContextBuildSingleParams single;
    ContextBuildSingleParams[] multi;
  }

  public static class ContextBuildSingleParams {
    public String kind;
    public String key;
    public String name;
    public Boolean anonymous;
    @SerializedName("private") public String[] privateAttrs;
    public Map<String, LDValue> custom;
  }
  
  public static class ContextBuildResponse {
    String output;
    String error;
  }
  
  public static class ContextConvertParams {
    String input;
  }
  
  public static class SecureModeHashParams {
    LDContext context;
  }
  
  public static class SecureModeHashResponse {
    String result;
  }

  public static class MigrationVariationParams {
    String key;
    LDContext context;
    String defaultStage;
  }

  public static class MigrationVariationResponse {
    String result;
  }

  public static class MigrationOperationParams {
    String operation;
    LDContext context;
    String key;
    String defaultStage;
    String payload;
    String readExecutionOrder;
    boolean trackConsistency;
    boolean trackLatency;
    boolean trackErrors;
    String oldEndpoint;
    String newEndpoint;
  }

  public static class MigrationOperationResponse {
    String result;
    String error;
  }
}
