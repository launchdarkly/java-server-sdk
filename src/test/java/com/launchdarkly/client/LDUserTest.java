package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.launchdarkly.client.TestUtil.jbool;
import static com.launchdarkly.client.TestUtil.jdouble;
import static com.launchdarkly.client.TestUtil.jint;
import static com.launchdarkly.client.TestUtil.js;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class LDUserTest {
  private static final Gson defaultGson = new Gson();
  
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
  public void canSetKey() {
    LDUser user = new LDUser.Builder("k").build();
    assertEquals("k", user.getKeyAsString());
  }
  
  @Test
  public void canSetSecondary() {
    LDUser user = new LDUser.Builder("key").secondary("s").build();
    assertEquals("s", user.getSecondary().stringValue());
  }

  @Test
  public void canSetPrivateSecondary() {
    LDUser user = new LDUser.Builder("key").privateSecondary("s").build();
    assertEquals("s", user.getSecondary().stringValue());
    assertEquals(ImmutableSet.of("secondary"), user.privateAttributeNames);
  }
  
  @Test
  public void canSetIp() {
    LDUser user = new LDUser.Builder("key").ip("i").build();
    assertEquals("i", user.getIp().stringValue());
  }
  
  @Test
  public void canSetPrivateIp() {
    LDUser user = new LDUser.Builder("key").privateIp("i").build();
    assertEquals("i", user.getIp().stringValue());
    assertEquals(ImmutableSet.of("ip"), user.privateAttributeNames);
  }

  @Test
  public void canSetEmail() {
    LDUser user = new LDUser.Builder("key").email("e").build();
    assertEquals("e", user.getEmail().stringValue());
  }
  
  @Test
  public void canSetPrivateEmail() {
    LDUser user = new LDUser.Builder("key").privateEmail("e").build();
    assertEquals("e", user.getEmail().stringValue());
    assertEquals(ImmutableSet.of("email"), user.privateAttributeNames);
  }

  @Test
  public void canSetName() {
    LDUser user = new LDUser.Builder("key").name("n").build();
    assertEquals("n", user.getName().stringValue());
  }
  
  @Test
  public void canSetPrivateName() {
    LDUser user = new LDUser.Builder("key").privateName("n").build();
    assertEquals("n", user.getName().stringValue());
    assertEquals(ImmutableSet.of("name"), user.privateAttributeNames);
  }

  @Test
  public void canSetAvatar() {
    LDUser user = new LDUser.Builder("key").avatar("a").build();
    assertEquals("a", user.getAvatar().stringValue());
  }
  
  @Test
  public void canSetPrivateAvatar() {
    LDUser user = new LDUser.Builder("key").privateAvatar("a").build();
    assertEquals("a", user.getAvatar().stringValue());
    assertEquals(ImmutableSet.of("avatar"), user.privateAttributeNames);
  }

  @Test
  public void canSetFirstName() {
    LDUser user = new LDUser.Builder("key").firstName("f").build();
    assertEquals("f", user.getFirstName().stringValue());
  }
  
  @Test
  public void canSetPrivateFirstName() {
    LDUser user = new LDUser.Builder("key").privateFirstName("f").build();
    assertEquals("f", user.getFirstName().stringValue());
    assertEquals(ImmutableSet.of("firstName"), user.privateAttributeNames);
  }

  @Test
  public void canSetLastName() {
    LDUser user = new LDUser.Builder("key").lastName("l").build();
    assertEquals("l", user.getLastName().stringValue());
  }
  
  @Test
  public void canSetPrivateLastName() {
    LDUser user = new LDUser.Builder("key").privateLastName("l").build();
    assertEquals("l", user.getLastName().stringValue());
    assertEquals(ImmutableSet.of("lastName"), user.privateAttributeNames);
  }

  @Test
  public void canSetAnonymous() {
    LDUser user = new LDUser.Builder("key").anonymous(true).build();
    assertEquals(true, user.getAnonymous().booleanValue());
  }

  @Test
  public void canSetCountry() {
    LDUser user = new LDUser.Builder("key").country(LDCountryCode.US).build();
    assertEquals("US", user.getCountry().stringValue());
  }

  @Test
  public void canSetCountryAsString() {
    LDUser user = new LDUser.Builder("key").country("US").build();
    assertEquals("US", user.getCountry().stringValue());
  }

  @Test
  public void canSetCountryAs3CharacterString() {
    LDUser user = new LDUser.Builder("key").country("USA").build();
    assertEquals("US", user.getCountry().stringValue());
  }

  @Test
  public void ambiguousCountryNameSetsCountryWithExactMatch() {
    // "United States" is ambiguous: can also match "United States Minor Outlying Islands"
    LDUser user = new LDUser.Builder("key").country("United States").build();
    assertEquals("US", user.getCountry().stringValue());
  }

  @Test
  public void ambiguousCountryNameSetsCountryWithPartialMatch() {
    // For an ambiguous match, we return the first match
    LDUser user = new LDUser.Builder("key").country("United St").build();
    assertNotNull(user.getCountry());
  }

  @Test
  public void partialUniqueMatchSetsCountry() {
    LDUser user = new LDUser.Builder("key").country("United States Minor").build();
    assertEquals("UM", user.getCountry().stringValue());
  }

  @Test
  public void invalidCountryNameDoesNotSetCountry() {
    LDUser user = new LDUser.Builder("key").country("East Jibip").build();
    assertEquals(LDValue.ofNull(), user.getCountry());
  }

  @Test
  public void canSetPrivateCountry() {
    LDUser user = new LDUser.Builder("key").privateCountry(LDCountryCode.US).build();
    assertEquals("US", user.getCountry().stringValue());
    assertEquals(ImmutableSet.of("country"), user.privateAttributeNames);
  }

  @Test
  public void canSetCustomString() {
    LDUser user = new LDUser.Builder("key").custom("thing", "value").build();
    assertEquals("value", user.getCustom("thing").stringValue());
  }
  
  @Test
  public void canSetPrivateCustomString() {
    LDUser user = new LDUser.Builder("key").privateCustom("thing", "value").build();
    assertEquals("value", user.getCustom("thing").stringValue());
    assertEquals(ImmutableSet.of("thing"), user.privateAttributeNames);
  }

  @Test
  public void canSetCustomInt() {
    LDUser user = new LDUser.Builder("key").custom("thing", 1).build();
    assertEquals(1, user.getCustom("thing").intValue());
  }
  
  @Test
  public void canSetPrivateCustomInt() {
    LDUser user = new LDUser.Builder("key").privateCustom("thing", 1).build();
    assertEquals(1, user.getCustom("thing").intValue());
    assertEquals(ImmutableSet.of("thing"), user.privateAttributeNames);
  }
  
  @Test
  public void canSetCustomBoolean() {
    LDUser user = new LDUser.Builder("key").custom("thing", true).build();
    assertEquals(true, user.getCustom("thing").booleanValue());
  }
  
  @Test
  public void canSetPrivateCustomBoolean() {
    LDUser user = new LDUser.Builder("key").privateCustom("thing", true).build();
    assertEquals(true, user.getCustom("thing").booleanValue());
    assertEquals(ImmutableSet.of("thing"), user.privateAttributeNames);
  }

  @Test
  public void canSetCustomJsonValue() {
    LDValue value = LDValue.buildObject().put("1", LDValue.of("x")).build();
    LDUser user = new LDUser.Builder("key").custom("thing", value).build();
    assertEquals(value, user.getCustom("thing"));
  }

  @Test
  public void canSetPrivateCustomJsonValue() {
    LDValue value = LDValue.buildObject().put("1", LDValue.of("x")).build();
    LDUser user = new LDUser.Builder("key").privateCustom("thing", value).build();
    assertEquals(value, user.getCustom("thing"));
    assertEquals(ImmutableSet.of("thing"), user.privateAttributeNames);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void canSetDeprecatedCustomJsonValue() {
    JsonObject value = new JsonObject();
    LDUser user = new LDUser.Builder("key").custom("thing", value).build();
    assertEquals(value, user.getCustom("thing").asJsonElement());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void canSetPrivateDeprecatedCustomJsonValue() {
    JsonObject value = new JsonObject();
    LDUser user = new LDUser.Builder("key").privateCustom("thing", value).build();
    assertEquals(value, user.getCustom("thing").asJsonElement());
    assertEquals(ImmutableSet.of("thing"), user.privateAttributeNames);
  }

  @Test
  public void testAllPropertiesInDefaultEncoding() {
    for (Map.Entry<LDUser, String> e: getUserPropertiesJsonMap().entrySet()) {
      JsonElement expected = defaultGson.fromJson(e.getValue(), JsonElement.class);
      JsonElement actual = defaultGson.toJsonTree(e.getKey());
      assertEquals(expected, actual);
    }
  }
  
  @Test
  public void testAllPropertiesInPrivateAttributeEncoding() {
    for (Map.Entry<LDUser, String> e: getUserPropertiesJsonMap().entrySet()) {
      JsonElement expected = defaultGson.fromJson(e.getValue(), JsonElement.class);
      JsonElement actual = LDConfig.DEFAULT.gson.toJsonTree(e.getKey());
      assertEquals(expected, actual);
    }
  }

  private Map<LDUser, String> getUserPropertiesJsonMap() {
    ImmutableMap.Builder<LDUser, String> builder = ImmutableMap.builder();
    builder.put(new LDUser.Builder("userkey").build(), "{\"key\":\"userkey\"}");
    builder.put(new LDUser.Builder("userkey").secondary("value").build(),
        "{\"key\":\"userkey\",\"secondary\":\"value\"}");
    builder.put(new LDUser.Builder("userkey").ip("value").build(),
        "{\"key\":\"userkey\",\"ip\":\"value\"}");
    builder.put(new LDUser.Builder("userkey").email("value").build(),
        "{\"key\":\"userkey\",\"email\":\"value\"}");
    builder.put(new LDUser.Builder("userkey").name("value").build(),
        "{\"key\":\"userkey\",\"name\":\"value\"}");
    builder.put(new LDUser.Builder("userkey").avatar("value").build(),
        "{\"key\":\"userkey\",\"avatar\":\"value\"}");
    builder.put(new LDUser.Builder("userkey").firstName("value").build(),
        "{\"key\":\"userkey\",\"firstName\":\"value\"}");
    builder.put(new LDUser.Builder("userkey").lastName("value").build(),
        "{\"key\":\"userkey\",\"lastName\":\"value\"}");
    builder.put(new LDUser.Builder("userkey").anonymous(true).build(),
        "{\"key\":\"userkey\",\"anonymous\":true}");
    builder.put(new LDUser.Builder("userkey").country(LDCountryCode.US).build(),
        "{\"key\":\"userkey\",\"country\":\"US\"}");
    builder.put(new LDUser.Builder("userkey").custom("thing", "value").build(),
        "{\"key\":\"userkey\",\"custom\":{\"thing\":\"value\"}}");
    return builder.build();
  }
  
  @Test
  public void defaultJsonEncodingHasPrivateAttributeNames() {
    LDUser user = new LDUser.Builder("userkey").privateName("x").privateEmail("y").build();
    String expected = "{\"key\":\"userkey\",\"name\":\"x\",\"email\":\"y\",\"privateAttributeNames\":[\"name\",\"email\"]}";
    assertEquals(defaultGson.fromJson(expected, JsonElement.class), defaultGson.toJsonTree(user));
  }
  
  @Test
  public void privateAttributeEncodingRedactsAllPrivateAttributes() {
    LDConfig config = new LDConfig.Builder().allAttributesPrivate(true).build();
    LDUser user = new LDUser.Builder("userkey")
        .secondary("s")
        .ip("i")
        .email("e")
        .name("n")
        .avatar("a")
        .firstName("f")
        .lastName("l")
        .anonymous(true)
        .country(LDCountryCode.US)
        .custom("thing", "value")
        .build();
    Set<String> redacted = ImmutableSet.of("secondary", "ip", "email", "name", "avatar", "firstName", "lastName", "country", "thing");

    JsonObject o = config.gson.toJsonTree(user).getAsJsonObject();
    assertEquals("userkey", o.get("key").getAsString());
    assertEquals(true, o.get("anonymous").getAsBoolean());
    for (String attr: redacted) {
      assertNull(o.get(attr));
    }
    assertNull(o.get("custom"));
    assertEquals(redacted, getPrivateAttrs(o));
  }
  
  @Test
  public void privateAttributeEncodingRedactsSpecificPerUserPrivateAttributes() {
    LDUser user = new LDUser.Builder("userkey")
        .email("e")
        .privateName("n")
        .custom("bar", 43)
        .privateCustom("foo", 42)
        .build();
    
    JsonObject o = LDConfig.DEFAULT.gson.toJsonTree(user).getAsJsonObject();
    assertEquals("e", o.get("email").getAsString());
    assertNull(o.get("name"));
    assertEquals(43, o.get("custom").getAsJsonObject().get("bar").getAsInt());
    assertNull(o.get("custom").getAsJsonObject().get("foo"));
    assertEquals(ImmutableSet.of("name", "foo"), getPrivateAttrs(o));
  }

  @Test
  public void privateAttributeEncodingRedactsSpecificGlobalPrivateAttributes() {
    LDConfig config = new LDConfig.Builder().privateAttributeNames("name", "foo").build();
    LDUser user = new LDUser.Builder("userkey")
        .email("e")
        .name("n")
        .custom("bar", 43)
        .custom("foo", 42)
        .build();
    
    JsonObject o = config.gson.toJsonTree(user).getAsJsonObject();
    assertEquals("e", o.get("email").getAsString());
    assertNull(o.get("name"));
    assertEquals(43, o.get("custom").getAsJsonObject().get("bar").getAsInt());
    assertNull(o.get("custom").getAsJsonObject().get("foo"));
    assertEquals(ImmutableSet.of("name", "foo"), getPrivateAttrs(o));
  }
  
  @Test
  public void getValueGetsBuiltInAttribute() {
    LDUser user = new LDUser.Builder("key")
        .name("Jane")
        .build();
    assertEquals(LDValue.of("Jane"), user.getValueForEvaluation("name"));
  }
  
  @Test
  public void getValueGetsCustomAttribute() {
    LDUser user = new LDUser.Builder("key")
        .custom("height", 5)
        .build();
    assertEquals(LDValue.of(5), user.getValueForEvaluation("height"));
  }
  
  @Test
  public void getValueGetsBuiltInAttributeEvenIfCustomAttrHasSameName() {
    LDUser user = new LDUser.Builder("key")
        .name("Jane")
        .custom("name", "Joan")
        .build();
    assertEquals(LDValue.of("Jane"), user.getValueForEvaluation("name"));
  }
  
  @Test
  public void getValueReturnsNullForCustomAttrIfThereAreNoCustomAttrs() {
    LDUser user = new LDUser.Builder("key")
        .name("Jane")
        .build();
    assertEquals(LDValue.ofNull(), user.getValueForEvaluation("height"));
  }

  @Test
  public void getValueReturnsNullForCustomAttrIfThereAreCustomAttrsButNotThisOne() {
    LDUser user = new LDUser.Builder("key")
        .name("Jane")
        .custom("eyes", "brown")
        .build();
    assertEquals(LDValue.ofNull(), user.getValueForEvaluation("height"));
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
  
  private Set<String> getPrivateAttrs(JsonObject o) {
    Type type = new TypeToken<List<String>>(){}.getType();
    return new HashSet<String>(defaultGson.<List<String>>fromJson(o.get("privateAttrs"), type));
  }
}
