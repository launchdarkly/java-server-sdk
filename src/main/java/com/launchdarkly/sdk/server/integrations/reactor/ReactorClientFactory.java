package com.launchdarkly.sdk.server.integrations.reactor;

import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * A factory for creating {@link LDReactorClient}s.
 */
public final class ReactorClientFactory {

    final Scheduler scheduler;

    /**
     * @param scheduler that will be used by {@link LDReactorClient}s for executing blocking work in a non-blocking
     *                  manner.
     */
    public ReactorClientFactory(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * See {@link LDClient#LDClient(String)}.
     */
    public Mono<LDReactorClient> makeClient(String sdkKey) {
        return Mono.fromCallable(() -> new LDReactorClient(this.scheduler, new LDClient(sdkKey))).subscribeOn(this.scheduler);
    }

    /**
     * See {@link LDClient#LDClient(String, LDConfig)
     */
    public Mono<LDReactorClient> makeClient(String sdkKey, LDConfig config) {
        return Mono.fromCallable(() -> new LDReactorClient(this.scheduler, new LDClient(sdkKey, config))).subscribeOn(this.scheduler);
    }

}
