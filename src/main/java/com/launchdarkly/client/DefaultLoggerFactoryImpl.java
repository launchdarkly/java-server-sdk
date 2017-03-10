package com.launchdarkly.client;

import org.slf4j.LoggerFactory;


public class DefaultLoggerFactoryImpl implements ILoggerFactory {
    public Logger getLogger(Class<?> classType) {
        return LoggerFactory.getLogger(classType);
    }
}
