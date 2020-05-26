package com.launchdarkly.sdk.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  
  private static final String BASE_NAME = LDClient.class.getName();

  /**
   * The default logger instance to use for SDK messages: "com.launchdarkly.sdk.server.LDClient"
   */
  static final Logger MAIN = LoggerFactory.getLogger(BASE_NAME);

  /**
   * The logger instance to use for messages related to polling, streaming, etc.: "com.launchdarkly.sdk.server.LDClient.DataSource"
   */
  static final Logger DATA_SOURCE = LoggerFactory.getLogger(BASE_NAME + ".DataSource");

  /**
   * The logger instance to use for messages related to data store problems: "com.launchdarkly.sdk.server.LDClient.DataStore"
   */
  static final Logger DATA_STORE = LoggerFactory.getLogger(BASE_NAME + ".DataStore");

  /**
   * The logger instance to use for messages related to flag evaluation: "com.launchdarkly.sdk.server.LDClient.Evaluation"
   */
  static final Logger EVALUATION = LoggerFactory.getLogger(BASE_NAME + ".Evaluation");

  /**
   * The logger instance to use for messages from the event processor: "com.launchdarkly.sdk.server.LDClient.Events"
   */
  static final Logger EVENTS = LoggerFactory.getLogger(BASE_NAME + ".Events");
}
