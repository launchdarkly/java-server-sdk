package com.launchdarkly.client.files;

import com.google.gson.JsonPrimitive;
import com.launchdarkly.client.LDClient;
import com.launchdarkly.client.LDConfig;
import com.launchdarkly.client.LDUser;

import org.junit.Test;

import static com.launchdarkly.client.files.TestData.FLAG_VALUE_1;
import static com.launchdarkly.client.files.TestData.FLAG_VALUE_1_KEY;
import static com.launchdarkly.client.files.TestData.FULL_FLAG_1_KEY;
import static com.launchdarkly.client.files.TestData.FULL_FLAG_1_VALUE;
import static com.launchdarkly.client.files.TestData.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ClientWithFileDataSourceTest {
  private static final LDUser user = new LDUser.Builder("userkey").build();

  private LDClient makeClient() throws Exception {
    FileDataSourceFactory fdsf = FileComponents.fileDataSource()
        .filePaths(resourceFilePath("all-properties.json"));
    LDConfig config = new LDConfig.Builder()
        .dataSource(fdsf)
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
