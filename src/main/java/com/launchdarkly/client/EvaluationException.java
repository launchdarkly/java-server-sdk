package com.launchdarkly.client;

/**
 * An error indicating an abnormal result from evaluating a feature
 */
class EvaluationException extends Exception {
  public EvaluationException(String message) {
    super(message);
  }
}
