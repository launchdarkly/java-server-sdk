package com.launchdarkly.sdk.internal.events;

import java.util.UUID;

class DiagnosticId {

  final String diagnosticId = UUID.randomUUID().toString();
  final String sdkKeySuffix;

  DiagnosticId(String sdkKey) {
    if (sdkKey == null) {
      sdkKeySuffix = null;
    } else {
      this.sdkKeySuffix = sdkKey.substring(Math.max(0, sdkKey.length() - 6));
    }
  }
}
