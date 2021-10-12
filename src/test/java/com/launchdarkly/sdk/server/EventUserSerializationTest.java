package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.UserAttribute;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.launchdarkly.sdk.server.JsonHelpers.gsonInstanceForEventsSerialization;
import static com.launchdarkly.sdk.server.TestComponents.defaultEventsConfig;
import static com.launchdarkly.sdk.server.TestComponents.makeEventsConfig;
import static com.launchdarkly.sdk.server.TestUtil.TEST_GSON_INSTANCE;
import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class EventUserSerializationTest {

  @Test
  public void testAllPropertiesInPrivateAttributeEncoding() {
    for (Map.Entry<LDUser, String> e: getUserPropertiesJsonMap().entrySet()) {
      String expected = e.getValue();
      String actual = TEST_GSON_INSTANCE.toJson(e.getKey());
      assertJsonEquals(expected, actual);
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
    builder.put(new LDUser.Builder("userkey").country("value").build(),
        "{\"key\":\"userkey\",\"country\":\"value\"}");
    builder.put(new LDUser.Builder("userkey").custom("thing", "value").build(),
        "{\"key\":\"userkey\",\"custom\":{\"thing\":\"value\"}}");
    return builder.build();
  }
  
  @Test
  public void defaultJsonEncodingHasPrivateAttributeNames() {
    LDUser user = new LDUser.Builder("userkey").privateName("x").build();
    String expected = "{\"key\":\"userkey\",\"name\":\"x\",\"privateAttributeNames\":[\"name\"]}";
    assertJsonEquals(expected, TEST_GSON_INSTANCE.toJson(user));
  }
  
  @Test
  public void privateAttributeEncodingRedactsAllPrivateAttributes() {
    EventsConfiguration config = makeEventsConfig(true, false, null);
    LDUser user = new LDUser.Builder("userkey")
        .secondary("s")
        .ip("i")
        .email("e")
        .name("n")
        .avatar("a")
        .firstName("f")
        .lastName("l")
        .anonymous(true)
        .country("USA")
        .custom("thing", "value")
        .build();
    Set<String> redacted = ImmutableSet.of("secondary", "ip", "email", "name", "avatar", "firstName", "lastName", "country", "thing");

    JsonObject o = gsonInstanceForEventsSerialization(config).toJsonTree(user).getAsJsonObject();
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
    
    JsonObject o = gsonInstanceForEventsSerialization(defaultEventsConfig()).toJsonTree(user).getAsJsonObject();
    assertEquals("e", o.get("email").getAsString());
    assertNull(o.get("name"));
    assertEquals(43, o.get("custom").getAsJsonObject().get("bar").getAsInt());
    assertNull(o.get("custom").getAsJsonObject().get("foo"));
    assertEquals(ImmutableSet.of("name", "foo"), getPrivateAttrs(o));
  }

  @Test
  public void privateAttributeEncodingRedactsSpecificGlobalPrivateAttributes() {
    EventsConfiguration config = makeEventsConfig(false, false,
        ImmutableSet.of(UserAttribute.NAME, UserAttribute.forName("foo")));
    LDUser user = new LDUser.Builder("userkey")
        .email("e")
        .name("n")
        .custom("bar", 43)
        .custom("foo", 42)
        .build();
    
    JsonObject o = gsonInstanceForEventsSerialization(config).toJsonTree(user).getAsJsonObject();
    assertEquals("e", o.get("email").getAsString());
    assertNull(o.get("name"));
    assertEquals(43, o.get("custom").getAsJsonObject().get("bar").getAsInt());
    assertNull(o.get("custom").getAsJsonObject().get("foo"));
    assertEquals(ImmutableSet.of("name", "foo"), getPrivateAttrs(o));
  }
  
  @Test
  public void privateAttributeEncodingWorksForMinimalUser() {
    EventsConfiguration config = makeEventsConfig(true, false, null);
    LDUser user = new LDUser("userkey");
    
    JsonObject o = gsonInstanceForEventsSerialization(config).toJsonTree(user).getAsJsonObject();
    JsonObject expected = new JsonObject();
    expected.addProperty("key", "userkey");
    assertEquals(expected, o);
  }
  
  @Test
  public void cannotDeserializeEventUser() {
    String json = "{}";
    LDUser user = gsonInstanceForEventsSerialization(defaultEventsConfig()).fromJson(json, LDUser.class);
    assertNull(user);
  }
  
  private Set<String> getPrivateAttrs(JsonObject o) {
    Type type = new TypeToken<HashSet<String>>(){}.getType();
    return TEST_GSON_INSTANCE.<HashSet<String>>fromJson(o.get("privateAttrs"), type);
  }
}
