package com.launchdarkly.client;

class Prerequisite {
  private String key;
  private int variation;

  private transient EvaluationReason.PrerequisiteFailed prerequisiteFailedReason;

  // We need this so Gson doesn't complain in certain java environments that restrict unsafe allocation
  Prerequisite() {}

  Prerequisite(String key, int variation) {
    this.key = key;
    this.variation = variation;
  }

  String getKey() {
    return key;
  }

  int getVariation() {
    return variation;
  }

  // This value is precomputed when we deserialize a FeatureFlag from JSON
  EvaluationReason.PrerequisiteFailed getPrerequisiteFailedReason() {
    return prerequisiteFailedReason;
  }

  void setPrerequisiteFailedReason(EvaluationReason.PrerequisiteFailed prerequisiteFailedReason) {
    this.prerequisiteFailedReason = prerequisiteFailedReason;
  }
}
