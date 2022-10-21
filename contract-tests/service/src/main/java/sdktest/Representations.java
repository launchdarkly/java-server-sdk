package sdktest;

import com.google.gson.annotations.SerializedName;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;

import java.net.URI;
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
  }
  
  public static class SdkConfigStreamParams {
    URI baseUri;
    long initialRetryDelayMs;
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
  
  public static class CommandParams {
    String command;
    EvaluateFlagParams evaluate;
    EvaluateAllFlagsParams evaluateAll;
    IdentifyEventParams identifyEvent;
    CustomEventParams customEvent;
    ContextBuildParams contextBuild;
    ContextConvertParams contextConvert;
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
}
