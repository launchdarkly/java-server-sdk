package com.launchdarkly.client;

import com.google.common.base.Joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

final class NewRelicReflector {

  private static Class<?> newRelic = null;

  private static Method addCustomParameter = null;

  private static final Logger logger = LoggerFactory.getLogger(NewRelicReflector.class);

  static {
    try {
      newRelic = Class.forName(getNewRelicClassName());
      addCustomParameter = newRelic.getDeclaredMethod("addCustomParameter", String.class, String.class);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      logger.info("No NewRelic agent detected");
    }
  }

  static String getNewRelicClassName() {
    // This ungainly logic is a workaround for the overly aggressive behavior of the Shadow plugin, which
    // will transform any class or package names passed to Class.forName() if they are string literals;
    // it will even transform the string "com".
    String com = Joiner.on("").join(new String[] { "c", "o", "m" });
    return Joiner.on(".").join(new String[] { com, "newrelic", "api", "agent", "NewRelic" });
  }

  static void annotateTransaction(String featureKey, String value) {
    if (addCustomParameter != null) {
      try {
        addCustomParameter.invoke(null, featureKey, value);
      } catch (Exception e) {
        logger.error("Unexpected error in LaunchDarkly NewRelic integration: {}", e.toString());
        logger.debug(e.toString(), e);
      }
    }
  }
}
