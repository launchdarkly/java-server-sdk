package com.launchdarkly.client.flag;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class FeatureFlagTest {


  @Test
  public void testParseOptionalIntegerfield() {
    FeatureFlag f = FeatureFlag.fromJson("{\n" +
        "  \"abc\": {\n" +
        "    \"key\": \"abc\",\n" +
        "    \"version\": 1,\n" +
        "    \"on\": true,\n" +
        "    \"prerequisites\": [],\n" +
        "    \"salt\": \"YWJj\",\n" +
        "    \"sel\": \"41e72130b42c414bac59fff3cf12a58e\",\n" +
        "    \"targets\": [\n" +
        "      {\n" +
        "        \"values\": [],\n" +
        "        \"variation\": 0\n" +
        "      },\n" +
        "      {\n" +
        "        \"values\": [],\n" +
        "        \"variation\": 1\n" +
        "      }\n" +
        "    ],\n" +
        "    \"rules\": [],\n" +
        "    \"fallthrough\": {\n" +
        "      \"clauses\": [],\n" +
        "      \"variation\": 1\n" +
        "    },\n" +
        "    \"offVariation\": null,\n" +
        "    \"variations\": [\n" +
        "      true,\n" +
        "      false\n" +
        "    ]\n" +
        "  }\n" +
        "}");
    assertNull(f.getOffVariation());
  }



}
