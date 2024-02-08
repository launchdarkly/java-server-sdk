package com.launchdarkly.sdk.server.integrations.reactor;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.FeatureFlagsState;
import com.launchdarkly.sdk.server.FlagsStateOption;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;

import reactor.core.publisher.Mono;

/**
 * A version of {@link LDClient} that is adapted to support reactive stream programming.
 */
public interface LDReactorClientInterface {

    /**
     * See {@link LDClient#isInitialized()}.
     *
     * @return see linked reference.
     */
    boolean isInitialized();

    /**
     * See {@link LDClient#track(String, LDContext)}.
     *
     * @param eventName see linked reference.
     * @param context see linked reference.
     */
    void track(String eventName, LDContext context);

    /**
     * See {@link LDClient#trackData(String, LDContext, LDValue)}.
     *
     * @param eventName see linked reference.
     * @param context see linked reference.
     * @param data see linked reference.
     */
    void trackData(String eventName, LDContext context, LDValue data);

    /**
     * See {@link LDClient#trackMetric(String, LDContext, LDValue, double)}.
     *
     * @param eventName see linked reference.
     * @param context see linked reference.
     * @param data see linked reference.
     * @param metricValue see linked reference.
     */
    void trackMetric(String eventName, LDContext context, LDValue data, double metricValue);

    /**
     * See {@link LDClient#identify(LDContext)}.
     *
     * @param context see linked reference.
     */
    void identify(LDContext context);

    /**
     * See {@link LDClient#allFlagsState(LDContext, FlagsStateOption...)}.
     *
     * @param context see linked reference.
     * @param options see linked reference.
     * @return a {@link Mono} that will emit the {@link FeatureFlagsState}.
     */
    Mono<FeatureFlagsState> allFlagsState(LDContext context, FlagsStateOption... options);

    /**
     * See {@link LDClient#boolVariation(String, LDContext, boolean)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<Boolean> boolVariation(String featureKey, LDContext context, boolean defaultValue);

    /**
     * See {@link LDClient#intVariation(String, LDContext, int)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<Integer> intVariation(String featureKey, LDContext context, int defaultValue);

    /**
     * See {@link LDClient#doubleVariation(String, LDContext, double)}
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<Double> doubleVariation(String featureKey, LDContext context, double defaultValue);

    /**
     * See {@link LDClient#stringVariation(String, LDContext, String)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<String> stringVariation(String featureKey, LDContext context, String defaultValue);

    /**
     * See {@link LDClient#jsonValueVariation(String, LDContext, LDValue)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<LDValue> jsonValueVariation(String featureKey, LDContext context, LDValue defaultValue);

    /**
     * See {@link LDClient#boolVariationDetail(String, LDContext, boolean)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<EvaluationDetail<Boolean>> boolVariationDetail(String featureKey, LDContext context, boolean defaultValue);

    /**
     * See {@link LDClient#intVariationDetail(String, LDContext, int)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<EvaluationDetail<Integer>> intVariationDetail(String featureKey, LDContext context, int defaultValue);

    /**
     * See {@link LDClient#doubleVariationDetail(String, LDContext, double)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<EvaluationDetail<Double>> doubleVariationDetail(String featureKey, LDContext context, double defaultValue);

    /**
     * See {@link LDClient#stringVariationDetail(String, LDContext, String)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<EvaluationDetail<String>> stringVariationDetail(String featureKey, LDContext context, String defaultValue);

    /**
     * See {@link LDClient#jsonValueVariationDetail(String, LDContext, LDValue)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    Mono<EvaluationDetail<LDValue>> jsonValueVariationDetail(String featureKey, LDContext context, LDValue defaultValue);

    /**
     * See {@link LDClient#isFlagKnown(String)}.
     *
     * @param featureKey see linked reference.
     * @return see linked reference.
     */
    boolean isFlagKnown(String featureKey);

    /**
     * See {@link LDClient#getFlagTracker()}.
     *
     * @return see linked reference.
     */
    FlagTracker getFlagTracker();

    /**
     * See {@link LDClient#getBigSegmentStoreStatusProvider()}.  Getting the {@link BigSegmentStoreStatusProvider} is
     * not a blocking operation, but function calls on the {@link BigSegmentStoreStatusProvider} may be.
     *
     * @return see linked reference.
     */
    BigSegmentStoreStatusProvider getBigSegmentStoreStatusProvider();

    /**
     * See {@link LDClient#getDataStoreStatusProvider()}.  Getting the {@link DataStoreStatusProvider} is not a blocking
     * operation, but function calls on the {@link DataStoreStatusProvider} may be.
     *
     * @return see linked reference.
     */
    DataStoreStatusProvider getDataStoreStatusProvider();

    /**
     * See {@link LDClient#getDataSourceStatusProvider()}.  Getting the {@link DataSourceStatusProvider} is not a
     * blocking operation, but function calls on the {@link DataSourceStatusProvider} may be.
     *
     * @return see linked reference.
     */
    DataSourceStatusProvider getDataSourceStatusProvider();

    /**
     * See {@link LDClient#close()}.
     *
     * @return a Mono that completes when {@link LDClient#close()} completes.
     */
    Mono<Void> close();

    /**
     * See {@link LDClient#flush()}.
     */
    void flush();

    /**
     * See {@link LDClient#isOffline()}.
     *
     * @return see linked reference.
     */
    boolean isOffline();

    /**
     * See {@link LDClient#secureModeHash(LDContext)}.
     *
     * @param context see linked reference.
     * @return see linked reference.
     */
    String secureModeHash(LDContext context);

    /**
     * See {@link LDClient#version()}.
     *
     * @return see linked reference.
     */
    String version();
}
