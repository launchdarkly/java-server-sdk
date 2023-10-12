package com.launchdarkly.sdk.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class EvalResultAndFlag {
  private final EvalResult result;
  private final DataModel.FeatureFlag flag;

  EvalResultAndFlag(@NotNull EvalResult result, @Nullable DataModel.FeatureFlag flag) {
    this.result = result;
    this.flag = flag;
  }

  EvalResult getResult() {
    return result;
  }

  DataModel.FeatureFlag getFlag() {
    return flag;
  }
}
