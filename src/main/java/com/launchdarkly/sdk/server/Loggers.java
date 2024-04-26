package com.launchdarkly.sdk.server;

/**
 * Static logger instances to be shared by implementation code in the main {@code com.launchdarkly.sdk.server}
 * package.
 * <p>
 * The goal here is 1. to centralize logger references rather than having many calls to
 * {@code LoggerFactory.getLogger()} all over the code, and 2. to encourage usage of a basic set of
 * logger names that are not tied to class names besides the main LDClient class. Most class names in
 * the SDK are package-private implementation details that are not meaningful to users, so in terms of
 * both being able to see the relevant area of functionality at a glance when reading a log and also
 * convenience in defining SLF4J logger name filters, it is preferable to use these stable names.
 * <p>
 * Code in other packages such as {@code com.launchdarkly.sdk.server.integrations} cannot use these
 * package-private fields, but should still use equivalent logger names as appropriate.
 */
abstract class Loggers {
  private Loggers() {}
  
  static final String BASE_LOGGER_NAME = LDClient.class.getName();
  static final String BIG_SEGMENTS_LOGGER_NAME = "BigSegments";
  static final String DATA_SOURCE_LOGGER_NAME = "DataSource";
  static final String DATA_STORE_LOGGER_NAME = "DataStore";
  static final String EVALUATION_LOGGER_NAME = "Evaluation";
  static final String EVENTS_LOGGER_NAME = "Events";
  static final String HOOKS_LOGGER_NAME = "Hooks";
}
