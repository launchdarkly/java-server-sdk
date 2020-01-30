package com.launchdarkly.client.integrations;

import com.launchdarkly.client.integrations.FileDataSourceParsing.YamlFlagFileParser;

@SuppressWarnings("javadoc")
public class FlagFileParserYamlTest extends FlagFileParserTestBase {
  public FlagFileParserYamlTest() {
    super(new YamlFlagFileParser(), ".yml");
  }
}
