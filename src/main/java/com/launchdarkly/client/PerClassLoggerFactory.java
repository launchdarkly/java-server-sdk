package com.launchdarkly.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;

public class PerClassLoggerFactory implements ILoggerFactory {
    private final ILoggerFactory factory;
    private final ConcurrentMap<Class<?>, Logger> staticLoggers;
    public PerClassLoggerFactory(ILoggerFactory factory) {
        this.factory = factory;
        this.staticLoggers = new ConcurrentHashMap<>();
    }

    public Logger getLogger(Class<?> klass){
        return staticLoggers.computeIfAbsent(klass, factory::getLogger);
    }
}
