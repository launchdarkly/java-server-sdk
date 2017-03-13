package com.launchdarkly.client;

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

public class LoggerFactory {

    private static AtomicReference<ILoggerFactory> factory = new AtomicReference<>();

    public static void install(ILoggerFactory factory) {
        LoggerFactory.factory.set(factory);
    }

    public static ILoggerFactory instance() {
        LoggerFactory.factory.compareAndSet(null, new DefaultLoggerFactoryImpl());
        return LoggerFactory.factory.get();
    }

    public static Logger getLogger(Class<?> className) {
        LoggerFactory.factory.compareAndSet(null, new DefaultLoggerFactoryImpl());
        return LoggerFactory.factory.get().getLogger(className);
    }
}
