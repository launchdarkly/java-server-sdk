package com.launchdarkly.client;

import com.launchdarkly.client.interfaces.DataSource;
import com.launchdarkly.client.interfaces.DataSourceFactory;
import com.launchdarkly.client.interfaces.DataStore;

interface DataSourceFactoryWithDiagnostics extends DataSourceFactory {
  DataSource createDataSource(String sdkKey, LDConfig config, DataStore featureStore,
                              DiagnosticAccumulator diagnosticAccumulator);
}
