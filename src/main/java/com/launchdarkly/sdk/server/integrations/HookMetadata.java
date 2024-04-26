package com.launchdarkly.sdk.server.integrations;

/**
 * Metadata about the {@link Hook} implementation.
 */
public abstract class HookMetadata {

    private final String name;

    public HookMetadata(String name) {
        this.name = name;
    }

    /**
     * @return a friendly name for the {@link Hook} this {@link HookMetadata} belongs to.
     */
    public String getName() {
        return name;
    }
}
