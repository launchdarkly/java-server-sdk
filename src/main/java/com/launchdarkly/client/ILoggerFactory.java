package com.launchdarkly.client;

import org.slf4j.Logger;

public interface ILoggerFactory {
    Logger getLogger(Class<?> className);
}

