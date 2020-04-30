package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.server.integrations.FileDataSourceParsing.YamlFlagFileParser;

@SuppressWarnings("javadoc")
public class FlagFileParserYamlTest extends FlagFileParserTestBase {
  public FlagFileParserYamlTest() {
    super(new YamlFlagFileParser(), ".yml");
  }
}
