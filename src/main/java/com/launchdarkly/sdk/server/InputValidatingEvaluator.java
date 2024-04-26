package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogValues;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.EvaluationReason.ErrorKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.subsystems.DataStore;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes;
import com.launchdarkly.sdk.server.subsystems.EventProcessor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.subsystems.EventProcessor.NO_VERSION;

/**
 * This is an evaluator that handles error cases related to initialization, parameter validation, evaluation result
 * type assertion, and runtime exceptions.
 */
class InputValidatingEvaluator implements EvaluatorInterface {

  private final Evaluator evaluator;
  private final DataStore store;
  private final LDLogger logger;

  // these are created at construction to avoid recreation during each evaluation
  private final EvaluationRecorder evaluationEventRecorderWithDetails;
  private final EvaluationRecorder evaluationEventRecorderWithoutDetails;
  static final EvaluationRecorder NO_OP_EVALUATION_EVENT_RECORDER = new EvaluationRecorder() {
  };

  /**
   * Creates an {@link InputValidatingEvaluator}
   *
   * @param store          will be used to get flag data
   * @param segmentStore   will be used to get segment data
   * @param eventProcessor will be used to record events during evaluations as necessary
   * @param logger         for logging messages and errors during evaluations
   */
  InputValidatingEvaluator(DataStore store, BigSegmentStoreWrapper segmentStore, @Nonnull EventProcessor eventProcessor, LDLogger logger) {
    this.evaluator = new Evaluator(new Evaluator.Getters() {
      public DataModel.FeatureFlag getFlag(String key) {
        return InputValidatingEvaluator.getFlag(store, key);
      }

      public DataModel.Segment getSegment(String key) {
        return InputValidatingEvaluator.getSegment(store, key);
      }

      public BigSegmentStoreWrapper.BigSegmentsQueryResult getBigSegments(String key) {
        return segmentStore == null ? null : segmentStore.getUserMembership(key);
      }

    }, logger);

    this.store = store;
    this.logger = logger;

    // these are created at construction to avoid recreation during each evaluation
    this.evaluationEventRecorderWithDetails = makeEvaluationRecorder(eventProcessor, true);
    this.evaluationEventRecorderWithoutDetails = makeEvaluationRecorder(eventProcessor, false);
  }

  @Override
  public EvalResultAndFlag evalAndFlag(String method, String flagKey, LDContext context, LDValue defaultValue,
                                       @Nullable LDValueType requireType, EvaluationOptions options) {
    // this implementation does not care for the method parameter

    // map options to appropriate event sink
    EvaluationRecorder sink;
    if (options == EvaluationOptions.EVENTS_WITH_REASONS) {
      sink = evaluationEventRecorderWithDetails;
    } else if (options == EvaluationOptions.EVENTS_WITHOUT_REASONS) {
      sink = evaluationEventRecorderWithoutDetails;
    } else {
      sink = NO_OP_EVALUATION_EVENT_RECORDER;
    }

    return evaluate(flagKey, context, defaultValue, requireType, sink);
  }

  /**
   * This function evaluates using the provided information and handles error cases related to initialization,
   * parameter validation, evaluation result type assertion, and runtime exceptions.
   *
   * @param flagKey      key of the flag that will be evaluated
   * @param context      the evaluation context
   * @param defaultValue the default value that will be returned in the case where the evaluator is unable to positively
   *                     evaluate the flag.  This may be because the flag is unknown, invalid context usage, or several
   *                     other potential reasons.
   * @param requireType  if not null, a value type assertion will be made
   * @param recorder     the recorder that will record during evaluation
   * @return an {@link EvalResultAndFlag} - guaranteed non-null
   */
  EvalResultAndFlag evaluate(String flagKey, LDContext context, LDValue defaultValue,
                             @Nullable LDValueType requireType, EvaluationRecorder recorder) {
    if (!store.isInitialized()) {
      logger.warn("Evaluation called before client initialized for feature flag \"{}\"; data store unavailable, returning default value", flagKey);
      recorder.recordEvaluationUnknownFlagError(flagKey, context, defaultValue, ErrorKind.CLIENT_NOT_READY);
      return new EvalResultAndFlag(EvalResult.error(ErrorKind.CLIENT_NOT_READY, defaultValue), null);
    }

    if (context == null) {
      logger.warn("Null context when evaluating flag \"{}\"; returning default value", flagKey);
      return new EvalResultAndFlag(EvalResult.error(ErrorKind.USER_NOT_SPECIFIED, defaultValue), null);
    }
    if (!context.isValid()) {
      logger.warn("Invalid context when evaluating flag \"{}\"; returning default value: " + context.getError(), flagKey);
      return new EvalResultAndFlag(EvalResult.error(ErrorKind.USER_NOT_SPECIFIED, defaultValue), null);
    }

    FeatureFlag featureFlag = null;
    try {
      featureFlag = getFlag(store, flagKey);
      if (featureFlag == null) {
        logger.info("Unknown feature flag \"{}\"; returning default value", flagKey);
        recorder.recordEvaluationUnknownFlagError(flagKey, context, defaultValue, ErrorKind.FLAG_NOT_FOUND);
        return new EvalResultAndFlag(EvalResult.error(ErrorKind.FLAG_NOT_FOUND, defaultValue), null);
      }

      EvalResult result = evaluator.evaluate(featureFlag, context, recorder);
      if (result.isNoVariation()) {
        result = EvalResult.of(defaultValue, result.getVariationIndex(), result.getReason());
      } else {
        LDValue value = result.getValue(); // guaranteed not to be an actual Java null, but can be LDValue.ofNull()
        if (requireType != null &&
            !value.isNull() &&
            value.getType() != requireType) {
          logger.error("Feature flag \"{}\"; evaluation expected result as {}, but got {}", flagKey, defaultValue.getType(), value.getType());
          recorder.recordEvaluationError(featureFlag, context, defaultValue, ErrorKind.WRONG_TYPE);
          return new EvalResultAndFlag(EvalResult.error(ErrorKind.WRONG_TYPE, defaultValue), featureFlag);
        }
      }

      recorder.recordEvaluation(featureFlag, context, result, defaultValue);
      return new EvalResultAndFlag(result, featureFlag);

    } catch (Exception e) {
      logger.error("Encountered exception while evaluating feature flag \"{}\": {}", flagKey,
          LogValues.exceptionSummary(e));
      logger.debug("{}", LogValues.exceptionTrace(e));
      if (featureFlag == null) {
        recorder.recordEvaluationUnknownFlagError(flagKey, context, defaultValue, ErrorKind.EXCEPTION);
      } else {
        recorder.recordEvaluationError(featureFlag, context, defaultValue, ErrorKind.EXCEPTION);
      }
      return new EvalResultAndFlag(EvalResult.of(defaultValue, NO_VARIATION, EvaluationReason.exception(e)), null);
    }
  }

  public FeatureFlagsState allFlagsState(LDContext context, FlagsStateOption... options) {
    FeatureFlagsState.Builder builder = FeatureFlagsState.builder(options);

    if (!store.isInitialized()) {
      logger.warn("allFlagsState() was called before client initialized; data store unavailable, returning no data");
      return builder.valid(false).build();
    }

    if (context == null) {
      logger.warn("allFlagsState() was called with null context! returning no data");
      return builder.valid(false).build();
    }
    if (!context.isValid()) {
      logger.warn("allFlagsState() was called with invalid context: " + context.getError());
      return builder.valid(false).build();
    }

    boolean clientSideOnly = FlagsStateOption.hasOption(options, FlagsStateOption.CLIENT_SIDE_ONLY);
    DataStoreTypes.KeyedItems<DataStoreTypes.ItemDescriptor> flags;
    try {
      flags = store.getAll(FEATURES);
    } catch (Exception e) {
      logger.error("Exception from data store when evaluating all flags: {}", LogValues.exceptionSummary(e));
      logger.debug(e.toString(), LogValues.exceptionTrace(e));
      return builder.valid(false).build();
    }

    for (Map.Entry<String, DataStoreTypes.ItemDescriptor> entry : flags.getItems()) {
      if (entry.getValue().getItem() == null) {
        continue; // deleted flag placeholder
      }
      DataModel.FeatureFlag flag = (DataModel.FeatureFlag) entry.getValue().getItem();
      if (clientSideOnly && !flag.isClientSide()) {
        continue;
      }
      try {
        // Note: a no op evaluation event recorder is provided as we don't want the all flag state to generate
        // any evaluation events.
        EvalResult result = evaluator.evaluate(flag, context, NO_OP_EVALUATION_EVENT_RECORDER);
        builder.addFlag(flag, result);
      } catch (Exception e) {
        logger.error("Exception caught for feature flag \"{}\" when evaluating all flags: {}", flag.getKey(),
            LogValues.exceptionSummary(e));
        logger.debug(e.toString(), LogValues.exceptionTrace(e));
        builder.addFlag(flag, EvalResult.of(LDValue.ofNull(), NO_VARIATION, EvaluationReason.exception(e)));
      }
    }
    return builder.build();
  }

  private static DataModel.FeatureFlag getFlag(DataStore store, String key) {
    DataStoreTypes.ItemDescriptor item = store.get(FEATURES, key);
    return item == null ? null : (DataModel.FeatureFlag) item.getItem();
  }

  private static DataModel.Segment getSegment(DataStore store, String key) {
    DataStoreTypes.ItemDescriptor item = store.get(SEGMENTS, key);
    return item == null ? null : (DataModel.Segment) item.getItem();
  }

  /**
   * This function will create an {@link EvaluationRecorder} that uses the provided processor internally and
   * adjusts behavior based on the other parameters provided.
   *
   * @param processor   that will be used internally
   * @param withReasons controls whether to include reasons when recording the events
   * @return the {@link EvaluationRecorder}
   */
  private static EvaluationRecorder makeEvaluationRecorder(EventProcessor processor, boolean withReasons) {
    return new EvaluationRecorder() {
      @Override
      public void recordEvaluation(FeatureFlag flag, LDContext context, EvalResult result, LDValue defaultValue) {
        processor.recordEvaluationEvent(
            context,
            flag.getKey(),
            flag.getVersion(),
            result.getVariationIndex(),
            result.getValue(),
            (withReasons || result.isForceReasonTracking()) ? result.getReason() : null,
            defaultValue,
            null,
            flag.isTrackEvents() || result.isForceReasonTracking(),
            flag.getDebugEventsUntilDate(),
            flag.isExcludeFromSummaries(),
            flag.getSamplingRatio()
        );
      }

      @Override
      public void recordPrerequisiteEvaluation(FeatureFlag flag, FeatureFlag prereqOfFlag, LDContext context, EvalResult result) {
        processor.recordEvaluationEvent(
            context,
            flag.getKey(),
            flag.getVersion(),
            result.getVariationIndex(),
            result.getValue(),
            (withReasons || result.isForceReasonTracking()) ? result.getReason() : null,
            LDValue.ofNull(), // note this default value ofNull is special because pre-req evals don't have defaulting
            prereqOfFlag.getKey(),
            flag.isTrackEvents() || result.isForceReasonTracking(),
            flag.getDebugEventsUntilDate(),
            flag.isExcludeFromSummaries(),
            flag.getSamplingRatio()
        );
      }

      @Override
      public void recordEvaluationError(FeatureFlag flag, LDContext context, LDValue defaultValue, ErrorKind errorKind) {
        processor.recordEvaluationEvent(
            context,
            flag.getKey(),
            flag.getVersion(),
            NO_VARIATION,
            defaultValue,
            withReasons ? EvaluationReason.error(errorKind) : null,
            defaultValue,
            null,
            flag.isTrackEvents(),
            flag.getDebugEventsUntilDate(),
            flag.isExcludeFromSummaries(),
            flag.getSamplingRatio()
        );
      }

      @Override
      public void recordEvaluationUnknownFlagError(String flagKey, LDContext context, LDValue defaultValue, ErrorKind errorKind) {
        processor.recordEvaluationEvent(
            context,
            flagKey,
            NO_VERSION,
            NO_VARIATION,
            defaultValue,
            withReasons ? EvaluationReason.error(errorKind) : null,
            defaultValue,
            null,
            false,
            null,
            false,
            null
        );
      }
    };
  }
}
