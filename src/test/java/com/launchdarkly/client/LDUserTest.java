package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.Map;

import static com.launchdarkly.client.TestUtil.jbool;
import static com.launchdarkly.client.TestUtil.jdouble;
import static com.launchdarkly.client.TestUtil.jint;
import static com.launchdarkly.client.TestUtil.js;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LDUserTest {

  private JsonPrimitive us = new JsonPrimitive(LDCountryCode.US.getAlpha2());

  @Test
  public void testLDUserConstructor() {
    LDUser user = new LDUser.Builder("key")
    .secondary("secondary")
    .ip("127.0.0.1")
    .firstName("Bob")
    .lastName("Loblaw")
    .email("bob@example.com")
    .name("Bob Loblaw")
    .avatar("image")
    .anonymous(false)
    .country("US")
    .custom("org", "LaunchDarkly")
    .build();
    
    assert(user.equals(new LDUser.Builder(user).build()));
  }

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
  public void testLDUserJsConfig() {
    LDConfig config = LDConfig.DEFAULT;
    LDUser user = new LDUser.Builder("key")
            .privateCustom("private-key", "private-value")
            .custom("public-key", "public-value")
            .build();

    JsonObject object = user.toJsConfig(config);

    assertEquals("key", object.get("key").getAsString());

    JsonArray privateAttributeNames = object.get("privateAttributeNames").getAsJsonArray();
    assertEquals(1, privateAttributeNames.size());
    assertEquals("private-key", privateAttributeNames.get(0).getAsString());

    assertEquals("private-value", object.get("custom").getAsJsonObject().get("private-key").getAsString());
    assertEquals("public-value", object.get("custom").getAsJsonObject().get("public-key").getAsString());
  }

  @Test
  public void testLDUserJsConfigAllAttributesPrivate() {
    LDConfig config = new LDConfig.Builder()
            .allAttributesPrivate(true)
            .build();
    LDUser user = new LDUser.Builder("key")
            .build();

    JsonObject object = user.toJsConfig(config);

    assertTrue(object.get("allAttributesPrivate").getAsBoolean());
  }

  @Test
  public void testLDUserJsConfigPrivate() {
    LDConfig config = new LDConfig.Builder()
            .privateAttributeNames("private-key")
            .build();
    LDUser user = new LDUser.Builder("key")
            .privateCustom("private-key2", "private-value2")
            .build();

    JsonObject object = user.toJsConfig(config);

    JsonArray privateAttributeNames = object.get("privateAttributeNames").getAsJsonArray();
    assertEquals(2, privateAttributeNames.size());
    assertEquals("private-key", privateAttributeNames.get(0).getAsString());
    assertEquals("private-key2", privateAttributeNames.get(1).getAsString());
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
  public void testLDUserAnonymousAttributeIsNeverPrivate() {
    LDConfig config = new LDConfig.Builder().allAttributesPrivate(true).build();
    LDUser user = new LDUser.Builder("key")
        .anonymous(true)
        .build();

    Type type = new TypeToken<Map<String, JsonElement>>(){}.getType();
    Map<String, JsonElement> privateJson = config.gson.fromJson(config.gson.toJson(user), type);

    assertEquals(privateJson.get("anonymous").getAsBoolean(), true);
    assertNull(privateJson.get("privateAttrs"));
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
  
  @Test
  public void getValueGetsBuiltInAttribute() {
    LDUser user = new LDUser.Builder("key")
        .name("Jane")
        .build();
    assertEquals(new JsonPrimitive("Jane"), user.getValueForEvaluation("name"));
  }
  
  @Test
  public void getValueGetsCustomAttribute() {
    LDUser user = new LDUser.Builder("key")
        .custom("height", 5)
        .build();
    assertEquals(new JsonPrimitive(5), user.getValueForEvaluation("height"));
  }
  
  @Test
  public void getValueGetsBuiltInAttributeEvenIfCustomAttrHasSameName() {
    LDUser user = new LDUser.Builder("key")
        .name("Jane")
        .custom("name", "Joan")
        .build();
    assertEquals(new JsonPrimitive("Jane"), user.getValueForEvaluation("name"));
  }
  
  @Test
  public void getValueReturnsNullIfNotFound() {
    LDUser user = new LDUser.Builder("key")
        .name("Jane")
        .build();
    assertNull(user.getValueForEvaluation("height"));
  }
  
  @Test
  public void canAddCustomAttrWithJsonValue() {
    JsonElement value = new JsonPrimitive("x");
    LDUser user = new LDUser.Builder("key")
        .custom("foo", value)
        .build();
    assertEquals(value, user.getCustom("foo"));
  }
  
  @Test
  public void canAddPrivateCustomAttrWithJsonValue() {
    JsonElement value = new JsonPrimitive("x");
    LDUser user = new LDUser.Builder("key")
        .privateCustom("foo", value)
        .build();
    assertEquals(value, user.getCustom("foo"));
    assertTrue(user.privateAttributeNames.contains("foo"));
  }
  
  @Test
  public void canAddCustomAttrWithListOfStrings() {
    LDUser user = new LDUser.Builder("key")
        .customString("foo", ImmutableList.of("a", "b"))
        .build();
    JsonElement expectedAttr = makeCustomAttrWithListOfValues("foo", js("a"), js("b"));
    JsonObject jo = LDConfig.DEFAULT.gson.toJsonTree(user).getAsJsonObject();
    assertEquals(expectedAttr, jo.get("custom"));
  }
  
  @Test
  public void canAddCustomAttrWithListOfNumbers() {
    LDUser user = new LDUser.Builder("key")
        .customNumber("foo", ImmutableList.<Number>of(new Integer(1), new Double(2)))
        .build();
    JsonElement expectedAttr = makeCustomAttrWithListOfValues("foo", jint(1), jdouble(2));
    JsonObject jo = LDConfig.DEFAULT.gson.toJsonTree(user).getAsJsonObject();
    assertEquals(expectedAttr, jo.get("custom"));
  }

  @Test
  public void canAddCustomAttrWithListOfMixedValues() {
    LDUser user = new LDUser.Builder("key")
        .customValues("foo", ImmutableList.<JsonElement>of(js("a"), jint(1), jbool(true)))
        .build();
    JsonElement expectedAttr = makeCustomAttrWithListOfValues("foo", js("a"), jint(1), jbool(true));
    JsonObject jo = LDConfig.DEFAULT.gson.toJsonTree(user).getAsJsonObject();
    assertEquals(expectedAttr, jo.get("custom"));
  }
  
  private JsonElement makeCustomAttrWithListOfValues(String name, JsonElement... values) {
    JsonObject ret = new JsonObject();
    JsonArray a = new JsonArray();
    for (JsonElement v: values) {
      a.add(v);
    }
    ret.add(name, a);
    return ret;
  }
}
