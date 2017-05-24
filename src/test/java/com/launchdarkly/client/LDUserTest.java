package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import org.junit.Test;

import java.lang.reflect.Type;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LDUserTest {

  private JsonPrimitive us = new JsonPrimitive(LDCountryCode.US.getAlpha2());

  @Test
  public void testValidCountryCodeSetsCountry() {
    LDUser user = new LDUser.Builder("key").country(LDCountryCode.US).build();

    assert(user.getCountry().equals(us));
  }


  @Test
  public void testValidCountryCodeStringSetsCountry() {
    LDUser user = new LDUser.Builder("key").country("US").build();

    assert(user.getCountry().equals(us));
  }

  @Test
  public void testValidCountryCode3SetsCountry() {
    LDUser user = new LDUser.Builder("key").country("USA").build();

    assert(user.getCountry().equals(us));
  }

  @Test
  public void testAmbiguousCountryNameSetsCountryWithExactMatch() {
    // "United States" is ambiguous: can also match "United States Minor Outlying Islands"
    LDUser user = new LDUser.Builder("key").country("United States").build();
    assert(user.getCountry().equals(us));
  }

  @Test
  public void testAmbiguousCountryNameSetsCountryWithPartialMatch() {
    // For an ambiguous match, we return the first match
    LDUser user = new LDUser.Builder("key").country("United St").build();
    assert(user.getCountry() != null);
  }


  @Test
  public void testPartialUniqueMatchSetsCountry() {
    LDUser user = new LDUser.Builder("key").country("United States Minor").build();
    assert(user.getCountry().equals(new JsonPrimitive(LDCountryCode.UM.getAlpha2())));
  }

  @Test
  public void testInvalidCountryNameDoesNotSetCountry() {
    LDUser user = new LDUser.Builder("key").country("East Jibip").build();
    assert(user.getCountry() == null);
  }

  @Test
  public void testLDUserJsonSerializationContainsCountryAsTwoDigitCode() {
    LDConfig config = LDConfig.DEFAULT;
    Gson gson = config.gson;
    LDUser user = new LDUser.Builder("key").country(LDCountryCode.US).build();

    String jsonStr = gson.toJson(user);
    Type type = new TypeToken<Map<String, JsonElement>>(){}.getType();
    Map<String, JsonElement> json = gson.fromJson(jsonStr, type);

    assert(json.get("country").equals(us));
  }

  @Test
  public void testLDUserCustomMarshalWithPrivateAttrsProducesEquivalentLDUserIfNoAttrsArePrivate() {
    LDConfig config = LDConfig.DEFAULT;
    LDUser user = new LDUser.Builder("key")
                            .anonymous(true)
                            .avatar("avatar")
                            .country(LDCountryCode.AC)
                            .ip("127.0.0.1")
                            .firstName("bob")
                            .lastName("loblaw")
                            .email("bob@example.com")
                            .custom("foo", 42)
                            .build();

    String jsonStr = new Gson().toJson(user);
    Type type = new TypeToken<Map<String, JsonElement>>(){}.getType();
    Map<String, JsonElement> json = config.gson.fromJson(jsonStr, type);
    Map<String, JsonElement> privateJson = config.gson.fromJson(config.gson.toJson(user), type);

    assertEquals(json, privateJson);
  }

  @Test
  public void testLDUserCustomMarshalWithAllPrivateAttributesReturnsKey() {
    LDConfig config = new LDConfig.Builder().allAttributesPrivate(true).build();
    LDUser user = new LDUser.Builder("key")
        .email("foo@bar.com")
        .custom("bar", 43)
        .build();

    Type type = new TypeToken<Map<String, JsonElement>>(){}.getType();
    Map<String, JsonElement> privateJson = config.gson.fromJson(config.gson.toJson(user), type);

    assertNull(privateJson.get("custom"));
    assertEquals(privateJson.get("key").getAsString(), "key");

    // email and custom are private
    assert(privateJson.get("privateAttrs").getAsJsonArray().size() == 2);
    assertNull(privateJson.get("email"));
  }

  @Test
  public void testLDUserCustomMarshalWithPrivateAttrsRedactsCorrectAttrs() {
    LDConfig config = LDConfig.DEFAULT;
    LDUser user = new LDUser.Builder("key")
        .privateCustom("foo", 42)
        .custom("bar", 43)
        .build();

    Type type = new TypeToken<Map<String, JsonElement>>(){}.getType();
    Map<String, JsonElement> privateJson = config.gson.fromJson(config.gson.toJson(user), type);

    assertNull(privateJson.get("custom").getAsJsonObject().get("foo"));
    assertEquals(privateJson.get("key").getAsString(), "key");
    assertEquals(privateJson.get("custom").getAsJsonObject().get("bar"), new JsonPrimitive(43));
  }

  @Test
  public void testLDUserCustomMarshalWithPrivateGlobalAttributesRedactsCorrectAttrs() {
    LDConfig config = new LDConfig.Builder().privateAttributeNames("foo", "bar").build();

    LDUser user = new LDUser.Builder("key")
        .privateCustom("foo", 42)
        .custom("bar", 43)
        .custom("baz", 44)
        .privateCustom("bum", 45)
        .build();

    Type type = new TypeToken<Map<String, JsonElement>>(){}.getType();
    Map<String, JsonElement> privateJson = config.gson.fromJson(config.gson.toJson(user), type);

    assertNull(privateJson.get("custom").getAsJsonObject().get("foo"));
    assertNull(privateJson.get("custom").getAsJsonObject().get("bar"));
    assertNull(privateJson.get("custom").getAsJsonObject().get("bum"));
    assertEquals(privateJson.get("custom").getAsJsonObject().get("baz"), new JsonPrimitive(44));
  }

  @Test
  public void testLDUserCustomMarshalWithBuiltInAttributesRedactsCorrectAttrs() {
    LDConfig config = LDConfig.DEFAULT;
    LDUser user = new LDUser.Builder("key")
        .privateEmail("foo@bar.com")
        .custom("bar", 43)
        .build();

    Type type = new TypeToken<Map<String, JsonElement>>(){}.getType();
    Map<String, JsonElement> privateJson = config.gson.fromJson(config.gson.toJson(user), type);
    assertNull(privateJson.get("email"));

  }
}
