package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.JsonFlagFileParser;

@SuppressWarnings("javadoc")
public class FlagFileParserJsonTest extends FlagFileParserTestBase {
  public FlagFileParserJsonTest() {
    super(new JsonFlagFileParser(), ".json");
  }
}
