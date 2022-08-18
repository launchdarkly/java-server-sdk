package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;

import org.junit.Test;

import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.FLAG_VALUE_1;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.FLAG_VALUE_1_KEY;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.FULL_FLAG_1_KEY;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.FULL_FLAG_1_VALUE;
import static com.launchdarkly.sdk.server.integrations.FileDataSourceTestData.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class ClientWithFileDataSourceTest {
  private static final LDContext user = LDContext.create("userkey");

  private LDClient makeClient() throws Exception {
    FileDataSourceBuilder fdsb = FileData.dataSource()
        .filePaths(resourceFilePath("all-properties.json"));
    LDConfig config = new LDConfig.Builder()
        .dataSource(fdsb)
        .events(Components.noEvents())
        .build();
    return new LDClient("sdkKey", config);
  }
  
  @Test
  public void fullFlagDefinitionEvaluatesAsExpected() throws Exception {
    try (LDClient client = makeClient()) {
      assertThat(client.jsonValueVariation(FULL_FLAG_1_KEY, user, LDValue.of("default")),
          equalTo(FULL_FLAG_1_VALUE));
    }
  }
  
  @Test
  public void simplifiedFlagEvaluatesAsExpected() throws Exception {
    try (LDClient client = makeClient()) {
      assertThat(client.jsonValueVariation(FLAG_VALUE_1_KEY, user, LDValue.of("default")),
          equalTo(FLAG_VALUE_1));
    }
  }
}
