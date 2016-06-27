package com.launchdarkly.client;

/**
 * An error indicating an abnormal result from evaluating a feature
 */
public class EvaluationError extends Exception {
  public EvaluationError(String message) {
    super(message);
  }
}
