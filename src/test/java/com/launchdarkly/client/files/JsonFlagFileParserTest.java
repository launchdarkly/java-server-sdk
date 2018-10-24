package com.launchdarkly.client.files;

public class JsonFlagFileParserTest extends FlagFileParserTestBase {
  public JsonFlagFileParserTest() {
    super(new JsonFlagFileParser(), ".json");
  }
}
