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
 * A thin wrapper of the {@link LDClient} that aims to make it compatible with reactive programming paradigms.
 *
 * To build a {@link LDReactorClient}, use a {@link ReactorClientFactory}.
 *
 * Methods that are potentially long running or that use IO have been wrapped to return {@link Mono}s
 * and will be executed on the scheduler provided by the {@link ReactorClientFactory}.
 */
public final class LDReactorClient {

    private final LDClient internalClient;
    private final Scheduler scheduler;

    LDReactorClient(Scheduler scheduler, LDClient internalClient) {
        this.scheduler = scheduler;
        this.internalClient = internalClient;
    }

    public boolean isInitialized() {
        return internalClient.isInitialized();
    }

    public void track(String eventName, LDContext context) {
        internalClient.track(eventName, context);
    }

    public void trackData(String eventName, LDContext context, LDValue data) {
        internalClient.trackData(eventName, context, data);
    }

    public void trackMetric(String eventName, LDContext context, LDValue data, double metricValue) {
        internalClient.trackMetric(eventName, context, data, metricValue);
    }

    public void identify(LDContext context) {
        internalClient.identify(context);
    }

    public Mono<FeatureFlagsState> allFlagsState(LDContext context, FlagsStateOption... options) {
        return Mono.fromCallable(() -> internalClient.allFlagsState(context, options)).subscribeOn(this.scheduler);
    }

    public Mono<Boolean> boolVariation(String featureKey, LDContext context, boolean defaultValue) {
        return Mono.fromCallable(() -> internalClient.boolVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<Integer> intVariation(String featureKey, LDContext context, int defaultValue) {
        return Mono.fromCallable(() -> internalClient.intVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<Double> doubleVariation(String featureKey, LDContext context, double defaultValue) {
        return Mono.fromCallable(() -> internalClient.doubleVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<String> stringVariation(String featureKey, LDContext context, String defaultValue) {
        return Mono.fromCallable(() -> internalClient.stringVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<LDValue> jsonValueVariation(String featureKey, LDContext context, LDValue defaultValue) {
        return Mono.fromCallable(() -> internalClient.jsonValueVariation(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<EvaluationDetail<Boolean>> boolVariationDetail(String featureKey, LDContext context, boolean defaultValue) {
        return Mono.fromCallable(() -> internalClient.boolVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<EvaluationDetail<Integer>> intVariationDetail(String featureKey, LDContext context, int defaultValue) {
        return Mono.fromCallable(() -> internalClient.intVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<EvaluationDetail<Double>> doubleVariationDetail(String featureKey, LDContext context, double defaultValue) {
        return Mono.fromCallable(() -> internalClient.doubleVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<EvaluationDetail<String>> stringVariationDetail(String featureKey, LDContext context, String defaultValue) {
        return Mono.fromCallable(() -> internalClient.stringVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public Mono<EvaluationDetail<LDValue>> jsonValueVariationDetail(String featureKey, LDContext context, LDValue defaultValue) {
        return Mono.fromCallable(() -> internalClient.jsonValueVariationDetail(featureKey, context, defaultValue)).subscribeOn(this.scheduler);
    }

    public boolean isFlagKnown(String featureKey) {
        return internalClient.isFlagKnown(featureKey);
    }

    public FlagTracker getFlagTracker() {
        return internalClient.getFlagTracker();
    }

    public BigSegmentStoreStatusProvider getBigSegmentStoreStatusProvider() {
        return internalClient.getBigSegmentStoreStatusProvider();
    }

    public DataStoreStatusProvider getDataStoreStatusProvider() {
        return internalClient.getDataStoreStatusProvider();
    }

    public DataSourceStatusProvider getDataSourceStatusProvider() {
        return internalClient.getDataSourceStatusProvider();
    }

    public Mono<Void> close() {
        return Mono.fromCallable((Callable<Void>) () -> {
            internalClient.close();
            return null;
        }).subscribeOn(this.scheduler);
    }

    public void flush() {
        internalClient.flush();
    }

    public boolean isOffline() {
        return internalClient.isOffline();
    }

    public String secureModeHash(LDContext context) {
        return internalClient.secureModeHash(context);
    }

    public String version() {
        return internalClient.version();
    }
}
