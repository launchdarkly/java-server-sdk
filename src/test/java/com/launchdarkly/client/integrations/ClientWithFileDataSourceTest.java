package com.launchdarkly.client.integrations;

import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.LDClient;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.LDUser;

import org.junit.Test;

import static com.launchdarkly.client.integrations.FileDataSourceTestData.FLAG_VALUE_1;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.FLAG_VALUE_1_KEY;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.FULL_FLAG_1_KEY;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.FULL_FLAG_1_VALUE;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class ClientWithFileDataSourceTest {
  private static final LDUser user = new LDUser.Builder("userkey").build();

  private LDClient makeClient() throws Exception {
    FileDataSourceBuilder fdsb = FileData.dataSource()
        .filePaths(resourceFilePath("all-properties.json"));
    LDConfig config = new LDConfig.Builder()
        .dataSource(fdsb)
        .sendEvents(false)
        .build();
    return new LDClient("sdkKey", config);
  }
  
  @Test
  public void fullFlagDefinitionEvaluatesAsExpected() throws Exception {
    try (LDClient client = makeClient()) {
      assertThat(client.jsonVariation(FULL_FLAG_1_KEY, user, new JsonPrimitive("default")),
          equalTo(FULL_FLAG_1_VALUE));
    }
  }
  
  @Test
  public void simplifiedFlagEvaluatesAsExpected() throws Exception {
    try (LDClient client = makeClient()) {
      assertThat(client.jsonVariation(FLAG_VALUE_1_KEY, user, new JsonPrimitive("default")),
          equalTo(FLAG_VALUE_1));
    }
  }
}
