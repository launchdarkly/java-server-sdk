package com.launchdarkly.sdk.server.integrations.reactor;

import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

public final class ReactorClientFactory {

    final Scheduler scheduler;

    public ReactorClientFactory(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    Mono<LDReactorClient> makeClient(String sdkKey) {
        return Mono.fromCallable(() -> new LDReactorClient(this.scheduler, new LDClient(sdkKey))).subscribeOn(this.scheduler);
    }

    Mono<LDReactorClient> makeClient(String sdkKey, LDConfig config) {
        return Mono.fromCallable(() -> new LDReactorClient(this.scheduler, new LDClient(sdkKey, config))).subscribeOn(this.scheduler);
    }

}
