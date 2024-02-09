package com.launchdarkly.sdk.server.integrations.reactor;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.FeatureFlagsState;
import com.launchdarkly.sdk.server.FlagsStateOption;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
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
 * Methods that are potentially long running or that use IO have been wrapped to return {@link Mono}s and will be
 * executed on the scheduler provided.  Methods that do not have a risk of blocking have not been wrapped and are
 * pass through.
 */
public final class LDReactorClient implements LDReactorClientInterface {

    private final LDClient wrappedClient;
    private final Scheduler scheduler;

    /**
     * Creates a client that uses the provided scheduler to execute functionality in a non-blocking manner.
     *
     * @param sdkKey the SDK key for your LaunchDarkly environment
     * @param scheduler that will execute wrapped client methods
     */
    public LDReactorClient(String sdkKey, Scheduler scheduler) {
        this.wrappedClient = new LDClient(sdkKey);
        this.scheduler = scheduler;
    }

    /**
     * Creates a client that uses the provided scheduler to execute functionality in a non-blocking manner.
     *
     * @param sdkKey the SDK key for your LaunchDarkly environment
     * @param config a client configuration object
     * @param scheduler that will execute wrapped client methods
     */
    public LDReactorClient(String sdkKey, LDConfig config, Scheduler scheduler) {
        this.wrappedClient = new LDClient(sdkKey, config);
        this.scheduler = scheduler;
    }

    @Override
    public boolean isInitialized() {
        return wrappedClient.isInitialized();
    }

    @Override
    public void track(String eventName, LDContext context) {
        wrappedClient.track(eventName, context);
    }

    @Override
    public void trackData(String eventName, LDContext context, LDValue data) {
        wrappedClient.trackData(eventName, context, data);
    }

    @Override
    public void trackMetric(String eventName, LDContext context, LDValue data, double metricValue) {
        wrappedClient.trackMetric(eventName, context, data, metricValue);
    }

    @Override
    public void identify(LDContext context) {
        wrappedClient.identify(context);
    }

    @Override
    public Mono<FeatureFlagsState> allFlagsState(LDContext context, FlagsStateOption... options) {
        return Mono.fromCallable(() -> wrappedClient.allFlagsState(context, options)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<Boolean> boolVariation(String featureKey, LDContext context, boolean defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.boolVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<Integer> intVariation(String featureKey, LDContext context, int defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.intVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<Double> doubleVariation(String featureKey, LDContext context, double defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.doubleVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<String> stringVariation(String featureKey, LDContext context, String defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.stringVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<LDValue> jsonValueVariation(String featureKey, LDContext context, LDValue defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.jsonValueVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<EvaluationDetail<Boolean>> boolVariationDetail(String featureKey, LDContext context, boolean defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.boolVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<EvaluationDetail<Integer>> intVariationDetail(String featureKey, LDContext context, int defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.intVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<EvaluationDetail<Double>> doubleVariationDetail(String featureKey, LDContext context, double defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.doubleVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<EvaluationDetail<String>> stringVariationDetail(String featureKey, LDContext context, String defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.stringVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public Mono<EvaluationDetail<LDValue>> jsonValueVariationDetail(String featureKey, LDContext context, LDValue defaultValue) {
        return Mono.fromCallable(() -> wrappedClient.jsonValueVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    @Override
    public boolean isFlagKnown(String featureKey) {
        return wrappedClient.isFlagKnown(featureKey);
    }

    @Override
    public FlagTracker getFlagTracker() {
        return wrappedClient.getFlagTracker();
    }

    @Override
    public BigSegmentStoreStatusProvider getBigSegmentStoreStatusProvider() {
        return wrappedClient.getBigSegmentStoreStatusProvider();
    }

    @Override
    public DataStoreStatusProvider getDataStoreStatusProvider() {
        return wrappedClient.getDataStoreStatusProvider();
    }

    @Override
    public DataSourceStatusProvider getDataSourceStatusProvider() {
        return wrappedClient.getDataSourceStatusProvider();
    }

    @Override
    public Mono<Void> close() {
        return Mono.fromCallable((Callable<Void>) () -> {
            wrappedClient.close();
            return null;
        }).subscribeOn(this.scheduler);
    }

    @Override
    public void flush() {
        wrappedClient.flush();
    }

    @Override
    public boolean isOffline() {
        return wrappedClient.isOffline();
    }

    @Override
    public String secureModeHash(LDContext context) {
        return wrappedClient.secureModeHash(context);
    }

    @Override
    public String version() {
        return wrappedClient.version();
    }
}
