package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.EvaluatorTestUtil.EvaluatorBuilder;

@SuppressWarnings("javadoc")
public class EvaluatorTestBase extends BaseTest {
  public EvaluatorBuilder evaluatorBuilder() {
    return new EvaluatorBuilder(testLogger);
  }
}
