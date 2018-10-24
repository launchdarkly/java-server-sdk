package com.launchdarkly.client.files;

public class YamlFlagFileParserTest extends FlagFileParserTestBase {
  public YamlFlagFileParserTest() {
    super(new YamlFlagFileParser(), ".yml");
  }
}
