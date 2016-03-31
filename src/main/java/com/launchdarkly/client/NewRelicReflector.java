package com.launchdarkly.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

final class NewRelicReflector {

  private static Class<?> newRelic = null;

  private static Method addCustomParameter = null;

  private static final Logger logger = LoggerFactory.getLogger(NewRelicReflector.class);


  static {
    try {
      newRelic = Class.forName("com.newrelic.api.agent.NewRelic");
      addCustomParameter = newRelic.getDeclaredMethod("addCustomParameter", String.class, String.class);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      logger.info("No NewRelic agent detected");
    }
  }

   static void annotateTransaction(String featureKey, String value) {
    if (addCustomParameter != null) {
      try {
        addCustomParameter.invoke(null, featureKey, value);
      } catch (Exception e) {
        logger.error("Unexpected error in LaunchDarkly NewRelic integration");
      }
    }
   }

}
