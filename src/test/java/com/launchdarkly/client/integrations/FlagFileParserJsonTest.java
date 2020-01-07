package com.launchdarkly.client.integrations;

import com.launchdarkly.client.integrations.FileDataSourceParsing.JsonFlagFileParser;

@SuppressWarnings("javadoc")
public class FlagFileParserJsonTest extends FlagFileParserTestBase {
  public FlagFileParserJsonTest() {
    super(new JsonFlagFileParser(), ".json");
  }
}
