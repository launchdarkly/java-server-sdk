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

import java.util.concurrent.Callable;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * A thin wrapper of the {@link LDClient} that aims to adapt it to reactive stream programming.
 *
 * To build a {@link LDReactorClient}, use a {@link ReactorClientFactory}.
 *
 * Methods that are potentially long running or that use IO have been wrapped to return {@link Mono}s and will be
 * executed on the scheduler provided to the {@link ReactorClientFactory}.  Methods that do not have a risk of
 * blocking have not been wrapped and are pass through.
 */
public final class LDReactorClient {

    private final LDClient wrappedClient;
    private final Scheduler scheduler;

    /**
     * Creates a wrapper that uses the provided scheduler to execute functionality in a non-blocking manner.  This
     * constructor is intentionally not public to enforce creation through the {@link ReactorClientFactory}
     * @param scheduler for executing background tasks
     * @param wrappedClient that will be wrapped
     */
    LDReactorClient(Scheduler scheduler, LDClient wrappedClient) {
        this.scheduler = scheduler;
        this.wrappedClient = wrappedClient;
    }

    /**
     * See {@link LDClient#isInitialized()}.
     *
     * @return see linked reference.
     */
    public boolean isInitialized() {
        return wrappedClient.isInitialized();
    }

    /**
     * See {@link LDClient#track(String, LDContext)}.
     *
     * @param eventName see linked reference.
     * @param context see linked reference.
     */
    public void track(String eventName, LDContext context) {
        wrappedClient.track(eventName, context);
    }

    /**
     * See {@link LDClient#trackData(String, LDContext, LDValue)}.
     *
     * @param eventName see linked reference.
     * @param context see linked reference.
     * @param data see linked reference.
     */
    public void trackData(String eventName, LDContext context, LDValue data) {
        wrappedClient.trackData(eventName, context, data);
    }

    /**
     * See {@link LDClient#trackMetric(String, LDContext, LDValue, double)}.
     *
     * @param eventName see linked reference.
     * @param context see linked reference.
     * @param data see linked reference.
     * @param metricValue see linked reference.
     */
    public void trackMetric(String eventName, LDContext context, LDValue data, double metricValue) {
        wrappedClient.trackMetric(eventName, context, data, metricValue);
    }

    /**
     * See {@link LDClient#identify(LDContext)}.
     *
     * @param context see linked reference.
     */
    public void identify(LDContext context) {
        wrappedClient.identify(context);
    }

    /**
     * See {@link LDClient#allFlagsState(LDContext, FlagsStateOption...)}.
     *
     * @param context see linked reference.
     * @param options see linked reference.
     * @return a {@link Mono} that will emit the {@link FeatureFlagsState}.
     */
    public Mono<FeatureFlagsState> allFlagsState(LDContext context, FlagsStateOption... options) {
        return Mono.fromCallable(() -> wrappedClient.allFlagsState(context, options)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#boolVariation(String, LDContext, boolean)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<Boolean> boolVariation(String featureKey, LDContext context, boolean defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.boolVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#intVariation(String, LDContext, int)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<Integer> intVariation(String featureKey, LDContext context, int defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.intVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#doubleVariation(String, LDContext, double)}
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<Double> doubleVariation(String featureKey, LDContext context, double defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.doubleVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#stringVariation(String, LDContext, String)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<String> stringVariation(String featureKey, LDContext context, String defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.stringVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#jsonValueVariation(String, LDContext, LDValue)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<LDValue> jsonValueVariation(String featureKey, LDContext context, LDValue defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.jsonValueVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#boolVariationDetail(String, LDContext, boolean)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<EvaluationDetail<Boolean>> boolVariationDetail(String featureKey, LDContext context, boolean defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.boolVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#intVariationDetail(String, LDContext, int)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<EvaluationDetail<Integer>> intVariationDetail(String featureKey, LDContext context, int defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.intVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#doubleVariationDetail(String, LDContext, double)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<EvaluationDetail<Double>> doubleVariationDetail(String featureKey, LDContext context, double defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.doubleVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#stringVariationDetail(String, LDContext, String)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<EvaluationDetail<String>> stringVariationDetail(String featureKey, LDContext context, String defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.stringVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#jsonValueVariationDetail(String, LDContext, LDValue)}.
     *
     * @param featureKey see linked reference.
     * @param context see linked reference.
     * @param defaultValue see linked reference.
     * @return a {@link Mono} that will emit the evaluation result.
     */
    public Mono<EvaluationDetail<LDValue>> jsonValueVariationDetail(String featureKey, LDContext context, LDValue defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.jsonValueVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#isFlagKnown(String)}.
     *
     * @param featureKey see linked reference.
     * @return see linked reference.
     */
    public boolean isFlagKnown(String featureKey) {
        return wrappedClient.isFlagKnown(featureKey);
    }

    /**
     * See {@link LDClient#getFlagTracker()}.
     *
     * @return see linked reference.
     */
    public FlagTracker getFlagTracker() {
        return wrappedClient.getFlagTracker();
    }

    /**
     * See {@link LDClient#getBigSegmentStoreStatusProvider()}.  Getting the {@link BigSegmentStoreStatusProvider} is
     * not a blocking operation, but function calls on the {@link BigSegmentStoreStatusProvider} may be.
     *
     * @return see linked reference.
     */
    public BigSegmentStoreStatusProvider getBigSegmentStoreStatusProvider() {
        return wrappedClient.getBigSegmentStoreStatusProvider();
    }

    /**
     * See {@link LDClient#getDataStoreStatusProvider()}.  Getting the {@link DataStoreStatusProvider} is not a blocking
     * operation, but function calls on the {@link DataStoreStatusProvider} may be.
     *
     * @return see linked reference.
     */
    public DataStoreStatusProvider getDataStoreStatusProvider() {
        return wrappedClient.getDataStoreStatusProvider();
    }

    /**
     * See {@link LDClient#getDataSourceStatusProvider()}.  Getting the {@link DataSourceStatusProvider} is not a
     * blocking operation, but function calls on the {@link DataSourceStatusProvider} may be.
     *
     * @return see linked reference.
     */
    public DataSourceStatusProvider getDataSourceStatusProvider() {
        return wrappedClient.getDataSourceStatusProvider();
    }

    /**
     * See {@link LDClient#close()}.
     *
     * @return a Mono that completes when {@link LDClient#close()} completes.
     */
    public Mono<Void> close() {
        return Mono.fromCallable((Callable<Void>) () -> {
            wrappedClient.close();
            return null;
        }).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#flush()}.
     */
    public void flush() {
        wrappedClient.flush();
    }

    /**
     * See {@link LDClient#isOffline()}.
     *
     * @return see linked reference.
     */
    public boolean isOffline() {
        return wrappedClient.isOffline();
    }

    /**
     * See {@link LDClient#secureModeHash(LDContext)}.
     *
     * @param context see linked reference.
     * @return see linked reference.
     */
    public String secureModeHash(LDContext context) {
        return wrappedClient.secureModeHash(context);
    }

    /**
     * See {@link LDClient#version()}.
     *
     * @return see linked reference.
     */
    public String version() {
        return wrappedClient.version();
    }
}
