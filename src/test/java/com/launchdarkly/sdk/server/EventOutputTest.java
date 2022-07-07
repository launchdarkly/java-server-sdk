package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.EventSummarizer.EventSummary;
import com.launchdarkly.sdk.server.interfaces.Event;
import com.launchdarkly.sdk.server.interfaces.Event.AliasEvent;
import com.launchdarkly.sdk.server.interfaces.Event.FeatureRequest;

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.TestComponents.defaultEventsConfig;
import static com.launchdarkly.sdk.server.TestComponents.makeEventsConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EventOutputTest {
  private static final Gson gson = new Gson();
  private static final String[] attributesThatCanBePrivate = new String[] {
      "avatar", "country", "custom1", "custom2", "email", "firstName", "ip", "lastName", "name", "secondary"
  };
  
  private LDUser.Builder userBuilderWithAllAttributes = new LDUser.Builder("userkey")
      .anonymous(true)
      .avatar("http://avatar")
      .country("US")
      .custom("custom1", "value1")
      .custom("custom2", "value2")
      .email("test@example.com")
      .firstName("first")
      .ip("1.2.3.4")
      .lastName("last")
      .name("me")
      .secondary("s");
  private static final LDValue userJsonWithAllAttributes = parseValue("{" +
      "\"key\":\"userkey\"," +
      "\"anonymous\":true," +
      "\"avatar\":\"http://avatar\"," +
      "\"country\":\"US\"," +
      "\"custom\":{\"custom1\":\"value1\",\"custom2\":\"value2\"}," +
      "\"email\":\"test@example.com\"," +
      "\"firstName\":\"first\"," +
      "\"ip\":\"1.2.3.4\"," +
      "\"lastName\":\"last\"," +
      "\"name\":\"me\"," +
      "\"secondary\":\"s\"" +
      "}");
  
  @Test
  public void allUserAttributesAreSerialized() throws Exception {
    testInlineUserSerialization(userBuilderWithAllAttributes.build(), userJsonWithAllAttributes,
        defaultEventsConfig());
  }

  @Test
  public void unsetUserAttributesAreNotSerialized() throws Exception {
      LDUser user = new LDUser("userkey");
      LDValue userJson = parseValue("{\"key\":\"userkey\"}");
      testInlineUserSerialization(user, userJson, defaultEventsConfig());
  }

  @Test
  public void userKeyIsSetInsteadOfUserWhenNotInlined() throws Exception {
    LDUser user = new LDUser.Builder("userkey").name("me").build();
    LDValue userJson = parseValue("{\"key\":\"userkey\",\"name\":\"me\"}");
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());

    Event.FeatureRequest featureEvent = EventFactory.DEFAULT.newFeatureRequestEvent(
        flagBuilder("flag").build(),
        user,
        EvalResult.of(LDValue.ofNull(), NO_VARIATION, EvaluationReason.off()),
        LDValue.ofNull());
    LDValue outputEvent = getSingleOutputEvent(f, featureEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("user"));
    assertEquals(LDValue.of(user.getKey()), outputEvent.get("userKey"));

    Event.Identify identifyEvent = EventFactory.DEFAULT.newIdentifyEvent(user);
    outputEvent = getSingleOutputEvent(f, identifyEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("userKey"));
    assertEquals(userJson, outputEvent.get("user"));

    Event.Custom customEvent = EventFactory.DEFAULT.newCustomEvent("custom", user, LDValue.ofNull(), null);
    outputEvent = getSingleOutputEvent(f, customEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("user"));
    assertEquals(LDValue.of(user.getKey()), outputEvent.get("userKey"));
    
    Event.Index indexEvent = new Event.Index(0, user);
    outputEvent = getSingleOutputEvent(f, indexEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("userKey"));
    assertEquals(userJson, outputEvent.get("user"));
  }

  @Test
  public void allAttributesPrivateMakesAttributesPrivate() throws Exception {
    LDUser user = userBuilderWithAllAttributes.build();
    EventsConfiguration config = makeEventsConfig(true, false, null);
    testPrivateAttributes(config, user, attributesThatCanBePrivate);
  }

  @Test
  public void globalPrivateAttributeNamesMakeAttributesPrivate() throws Exception {
    LDUser user = userBuilderWithAllAttributes.build();
    for (String attrName: attributesThatCanBePrivate) {
      EventsConfiguration config = makeEventsConfig(false, false, ImmutableSet.of(UserAttribute.forName(attrName)));
      testPrivateAttributes(config, user, attrName);
    }
  }
  
  @Test
  public void perUserPrivateAttributesMakeAttributePrivate() throws Exception {
    LDUser baseUser = userBuilderWithAllAttributes.build();
    EventsConfiguration config = defaultEventsConfig();
    
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateAvatar("x").build(), "avatar");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateCountry("US").build(), "country");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateCustom("custom1", "x").build(), "custom1");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateEmail("x").build(), "email");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateFirstName("x").build(), "firstName");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateLastName("x").build(), "lastName");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateName("x").build(), "name");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateSecondary("x").build(), "secondary");
  }
  
  private void testPrivateAttributes(EventsConfiguration config, LDUser user, String... privateAttrNames) throws IOException {
    EventOutputFormatter f = new EventOutputFormatter(config);
    Set<String> privateAttrNamesSet = ImmutableSet.copyOf(privateAttrNames);
    Event.Identify identifyEvent = EventFactory.DEFAULT.newIdentifyEvent(user);
    LDValue outputEvent = getSingleOutputEvent(f, identifyEvent);
    LDValue userJson = outputEvent.get("user");
    
    ObjectBuilder o = LDValue.buildObject();
    for (String key: userJsonWithAllAttributes.keys()) {
      LDValue value = userJsonWithAllAttributes.get(key);
      if (!privateAttrNamesSet.contains(key)) {
        if (key.equals("custom")) {
          ObjectBuilder co = LDValue.buildObject();
          for (String customKey: value.keys()) {
            if (!privateAttrNamesSet.contains(customKey)) {
              co.put(customKey, value.get(customKey));
            }
          }
          LDValue custom = co.build();
          if (custom.size() > 0) {
            o.put(key, custom);
          }
        } else {
          o.put(key, value);
        }
      }
    }
    o.put("privateAttrs", LDValue.Convert.String.arrayOf(privateAttrNames));
    
    assertEquals(o.build(), userJson);
  }
  
  private ObjectBuilder buildFeatureEventProps(String key, String userKey) {
    return LDValue.buildObject()
        .put("kind", "feature")
        .put("key", key)
        .put("creationDate", 100000)
        .put("userKey", userKey);
  }

  private ObjectBuilder buildFeatureEventProps(String key) {
    return buildFeatureEventProps(key, "userkey");
  }
  
  @Test
  public void featureEventIsSerialized() throws Exception {
    EventFactory factory = eventFactoryWithTimestamp(100000, false);
    EventFactory factoryWithReason = eventFactoryWithTimestamp(100000, true);
    DataModel.FeatureFlag flag = flagBuilder("flag").version(11).build();
    LDUser user = new LDUser.Builder("userkey").name("me").build();
    LDUser anon = new LDUser.Builder("anonymouskey").anonymous(true).build();
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());
    
    FeatureRequest feWithVariation = factory.newFeatureRequestEvent(flag, user,
        EvalResult.of(LDValue.of("flagvalue"), 1, EvaluationReason.off()),
        LDValue.of("defaultvalue"));
    LDValue feJson1 = buildFeatureEventProps("flag")
        .put("version", 11)
        .put("variation", 1)
        .put("value", "flagvalue")
        .put("default", "defaultvalue")
        .build();
    assertEquals(feJson1, getSingleOutputEvent(f, feWithVariation));

    FeatureRequest feWithoutVariationOrDefault = factory.newFeatureRequestEvent(flag, user,
        EvalResult.of(LDValue.of("flagvalue"), NO_VARIATION, EvaluationReason.off()),
        LDValue.ofNull());
    LDValue feJson2 = buildFeatureEventProps("flag")
        .put("version", 11)
        .put("value", "flagvalue")
        .build();
    assertEquals(feJson2, getSingleOutputEvent(f, feWithoutVariationOrDefault));

    FeatureRequest feWithReason = factoryWithReason.newFeatureRequestEvent(flag, user,
        EvalResult.of(LDValue.of("flagvalue"), 1, EvaluationReason.fallthrough()),
        LDValue.of("defaultvalue"));
    LDValue feJson3 = buildFeatureEventProps("flag")
        .put("version", 11)
        .put("variation", 1)
        .put("value", "flagvalue")
        .put("default", "defaultvalue")
        .put("reason", LDValue.buildObject().put("kind", "FALLTHROUGH").build())
        .build();
    assertEquals(feJson3, getSingleOutputEvent(f, feWithReason));

    FeatureRequest feUnknownFlag = factoryWithReason.newUnknownFeatureRequestEvent("flag", user,
        LDValue.of("defaultvalue"), EvaluationReason.ErrorKind.FLAG_NOT_FOUND);
    LDValue feJson4 = buildFeatureEventProps("flag")
        .put("value", "defaultvalue")
        .put("default", "defaultvalue")
        .put("reason", LDValue.buildObject().put("kind", "ERROR").put("errorKind", "FLAG_NOT_FOUND").build())
        .build();
    assertEquals(feJson4, getSingleOutputEvent(f, feUnknownFlag));

    Event.FeatureRequest debugEvent = EventFactory.newDebugEvent(feWithVariation);
    
    LDValue feJson5 = LDValue.buildObject()
        .put("kind", "debug")
        .put("key", "flag")
        .put("creationDate", 100000)
        .put("version", 11)
        .put("variation", 1)
        .put("user", LDValue.buildObject().put("key", "userkey").put("name", "me").build())
        .put("value", "flagvalue")
        .put("default", "defaultvalue")
        .build();
    assertEquals(feJson5, getSingleOutputEvent(f, debugEvent));
    
    DataModel.FeatureFlag parentFlag = flagBuilder("parent").build();
    Event.FeatureRequest prereqEvent = factory.newPrerequisiteFeatureRequestEvent(flag, user,
        EvalResult.of(LDValue.of("flagvalue"), 1, EvaluationReason.fallthrough()), parentFlag);
    LDValue feJson6 = buildFeatureEventProps("flag")
        .put("version", 11)
        .put("variation", 1)
        .put("value", "flagvalue")
        .put("prereqOf", "parent")
        .build();
    assertEquals(feJson6, getSingleOutputEvent(f, prereqEvent));

    Event.FeatureRequest prereqWithReason = factoryWithReason.newPrerequisiteFeatureRequestEvent(flag, user,
        EvalResult.of(LDValue.of("flagvalue"), 1, EvaluationReason.fallthrough()), parentFlag);
    LDValue feJson7 = buildFeatureEventProps("flag")
        .put("version", 11)
        .put("variation", 1)
        .put("value", "flagvalue")
        .put("reason", LDValue.buildObject().put("kind", "FALLTHROUGH").build())
        .put("prereqOf", "parent")
        .build();
    assertEquals(feJson7, getSingleOutputEvent(f, prereqWithReason));

    Event.FeatureRequest prereqWithoutResult = factoryWithReason.newPrerequisiteFeatureRequestEvent(flag, user,
        null, parentFlag);
    LDValue feJson8 = buildFeatureEventProps("flag")
        .put("version", 11)
        .put("prereqOf", "parent")
        .build();
    assertEquals(feJson8, getSingleOutputEvent(f, prereqWithoutResult));

    FeatureRequest anonFeWithVariation = factory.newFeatureRequestEvent(flag, anon,
        EvalResult.of(LDValue.of("flagvalue"), 1, EvaluationReason.off()),
        LDValue.of("defaultvalue"));
    LDValue anonFeJson1 = buildFeatureEventProps("flag", "anonymouskey")
        .put("version", 11)
        .put("variation", 1)
        .put("value", "flagvalue")
        .put("default", "defaultvalue")
        .put("contextKind", "anonymousUser")
        .build();
    assertEquals(anonFeJson1, getSingleOutputEvent(f, anonFeWithVariation));
  }

  @Test
  public void identifyEventIsSerialized() throws IOException {
    EventFactory factory = eventFactoryWithTimestamp(100000, false);
    LDUser user = new LDUser.Builder("userkey").name("me").build();
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());

    Event.Identify ie = factory.newIdentifyEvent(user);
    LDValue ieJson = parseValue("{" +
        "\"kind\":\"identify\"," +
        "\"creationDate\":100000," +
        "\"key\":\"userkey\"," +
        "\"user\":{\"key\":\"userkey\",\"name\":\"me\"}" +
        "}");
    assertEquals(ieJson, getSingleOutputEvent(f, ie));
  }

  @Test
  public void customEventIsSerialized() throws IOException {
    EventFactory factory = eventFactoryWithTimestamp(100000, false);
    LDUser user = new LDUser.Builder("userkey").name("me").build();
    LDUser anon = new LDUser.Builder("userkey").name("me").anonymous(true).build();
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());

    Event.Custom ceWithoutData = factory.newCustomEvent("customkey", user, LDValue.ofNull(), null);
    LDValue ceJson1 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"userKey\":\"userkey\"" +
        "}");
    assertEquals(ceJson1, getSingleOutputEvent(f, ceWithoutData));

    Event.Custom ceWithData = factory.newCustomEvent("customkey", user, LDValue.of("thing"), null);
    LDValue ceJson2 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"userKey\":\"userkey\"," +
        "\"data\":\"thing\"" +
        "}");
    assertEquals(ceJson2, getSingleOutputEvent(f, ceWithData));

    Event.Custom ceWithMetric = factory.newCustomEvent("customkey", user, LDValue.ofNull(), 2.5);
    LDValue ceJson3 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"userKey\":\"userkey\"," +
        "\"metricValue\":2.5" +
        "}");
    assertEquals(ceJson3, getSingleOutputEvent(f, ceWithMetric));

    Event.Custom ceWithDataAndMetric = factory.newCustomEvent("customkey", user, LDValue.of("thing"), 2.5);
    LDValue ceJson4 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"userKey\":\"userkey\"," +
        "\"data\":\"thing\"," +
        "\"metricValue\":2.5" +
        "}");
    assertEquals(ceJson4, getSingleOutputEvent(f, ceWithDataAndMetric));

    Event.Custom ceWithDataAndMetricAnon = factory.newCustomEvent("customkey", anon, LDValue.of("thing"), 2.5);
    LDValue ceJson5 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"userKey\":\"userkey\"," +
        "\"data\":\"thing\"," +
        "\"metricValue\":2.5," +
        "\"contextKind\":\"anonymousUser\"" +
        "}");
    assertEquals(ceJson5, getSingleOutputEvent(f, ceWithDataAndMetricAnon));
  }

  @Test
  public void summaryEventIsSerialized() throws Exception {
    EventSummary summary = new EventSummary();
    summary.noteTimestamp(1001);

    // Note use of "new String()" to ensure that these flag keys are not interned, as string literals normally are -
    // we found a bug where strings were being compared by reference equality.
    
    summary.incrementCounter(new String("first"), 1, 11, LDValue.of("value1a"), LDValue.of("default1"));

    summary.incrementCounter(new String("second"), 1, 21, LDValue.of("value2a"), LDValue.of("default2"));

    summary.incrementCounter(new String("first"), 1, 11, LDValue.of("value1a"), LDValue.of("default1"));
    summary.incrementCounter(new String("first"), 1, 12, LDValue.of("value1a"), LDValue.of("default1"));

    summary.incrementCounter(new String("second"), 2, 21, LDValue.of("value2b"), LDValue.of("default2"));
    summary.incrementCounter(new String("second"), -1, 21, LDValue.of("default2"), LDValue.of("default2")); // flag exists (has version), but eval failed (no variation)

    summary.incrementCounter(new String("third"), -1, -1, LDValue.of("default3"), LDValue.of("default3")); // flag doesn't exist (no version)

    summary.noteTimestamp(1000);
    summary.noteTimestamp(1002);

    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());
    StringWriter w = new StringWriter();
    int count = f.writeOutputEvents(new Event[0], summary, w);
    assertEquals(1, count);
    LDValue outputEvent = parseValue(w.toString()).get(0);

    assertEquals("summary", outputEvent.get("kind").stringValue());
    assertEquals(1000, outputEvent.get("startDate").intValue());
    assertEquals(1002, outputEvent.get("endDate").intValue());

    LDValue featuresJson = outputEvent.get("features");
    assertEquals(3, featuresJson.size());

    LDValue firstJson = featuresJson.get("first");
    assertEquals("default1", firstJson.get("default").stringValue());
    assertThat(firstJson.get("counters").values(), containsInAnyOrder(
        parseValue("{\"value\":\"value1a\",\"variation\":1,\"version\":11,\"count\":2}"),
        parseValue("{\"value\":\"value1a\",\"variation\":1,\"version\":12,\"count\":1}")
    ));

    LDValue secondJson = featuresJson.get("second");
    assertEquals("default2", secondJson.get("default").stringValue());
    assertThat(secondJson.get("counters").values(), containsInAnyOrder(
            parseValue("{\"value\":\"value2a\",\"variation\":1,\"version\":21,\"count\":1}"),
            parseValue("{\"value\":\"value2b\",\"variation\":2,\"version\":21,\"count\":1}"),
            parseValue("{\"value\":\"default2\",\"version\":21,\"count\":1}")
        ));
        
    LDValue thirdJson = featuresJson.get("third");
    assertEquals("default3", thirdJson.get("default").stringValue());
    assertThat(thirdJson.get("counters").values(), contains(
        parseValue("{\"unknown\":true,\"value\":\"default3\",\"count\":1}")
    ));
  }
  
  @Test
  public void unknownEventClassIsNotSerialized() throws Exception {
    // This shouldn't be able to happen in reality.
    Event event = new FakeEventClass(1000, new LDUser("user"));
    
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());
    StringWriter w = new StringWriter();
    f.writeOutputEvents(new Event[] { event }, new EventSummary(), w);
    
    assertEquals("[]", w.toString());
  }
  
  @Test
  public void aliasEventIsSerialized() throws IOException {
    EventFactory factory = eventFactoryWithTimestamp(1000, false);
    LDUser user1 = new LDUser.Builder("bob-key").build();
    LDUser user2 = new LDUser.Builder("jeff-key").build();
    LDUser anon1 = new LDUser.Builder("bob-key-anon").anonymous(true).build();
    LDUser anon2 = new LDUser.Builder("jeff-key-anon").anonymous(true).build();
    AliasEvent userToUser = factory.newAliasEvent(user1, user2);
    AliasEvent userToAnon = factory.newAliasEvent(anon1, user1);
    AliasEvent anonToUser = factory.newAliasEvent(user1, anon1);
    AliasEvent anonToAnon = factory.newAliasEvent(anon1, anon2);

    EventOutputFormatter fmt = new EventOutputFormatter(defaultEventsConfig());

    LDValue userToUserExpected = parseValue("{" +
      "\"kind\":\"alias\"," +
      "\"creationDate\":1000," +
      "\"key\":\"bob-key\"," +
      "\"contextKind\":\"user\"," +
      "\"previousKey\":\"jeff-key\"," +
      "\"previousContextKind\":\"user\"" +
      "}");

    assertEquals(userToUserExpected, getSingleOutputEvent(fmt, userToUser));

    LDValue userToAnonExpected = parseValue("{" +
      "\"kind\":\"alias\"," +
      "\"creationDate\":1000," +
      "\"key\":\"bob-key-anon\"," +
      "\"contextKind\":\"anonymousUser\"," +
      "\"previousKey\":\"bob-key\"," +
      "\"previousContextKind\":\"user\"" +
      "}");

    assertEquals(userToAnonExpected, getSingleOutputEvent(fmt, userToAnon));

    LDValue anonToUserExpected = parseValue("{" +
      "\"kind\":\"alias\"," +
      "\"creationDate\":1000," +
      "\"key\":\"bob-key\"," +
      "\"contextKind\":\"user\"," +
      "\"previousKey\":\"bob-key-anon\"," +
      "\"previousContextKind\":\"anonymousUser\"" +
      "}");

    assertEquals(anonToUserExpected, getSingleOutputEvent(fmt, anonToUser));

    LDValue anonToAnonExpected = parseValue("{" +
      "\"kind\":\"alias\"," +
      "\"creationDate\":1000," +
      "\"key\":\"bob-key-anon\"," +
      "\"contextKind\":\"anonymousUser\"," +
      "\"previousKey\":\"jeff-key-anon\"," +
      "\"previousContextKind\":\"anonymousUser\"" +
      "}");

    assertEquals(anonToAnonExpected, getSingleOutputEvent(fmt, anonToAnon));
  }

  private static class FakeEventClass extends Event {
    public FakeEventClass(long creationDate, LDUser user) {
      super(creationDate, user);
    }
  }
  
  private static LDValue parseValue(String json) {
    return gson.fromJson(json, LDValue.class);
  }

  private EventFactory eventFactoryWithTimestamp(final long timestamp, final boolean includeReasons) {
    return new EventFactory.Default(includeReasons, () -> timestamp);
  }
  
  private LDValue getSingleOutputEvent(EventOutputFormatter f, Event event) throws IOException {
    StringWriter w = new StringWriter();
    int count = f.writeOutputEvents(new Event[] { event }, new EventSummary(), w);
    assertEquals(1, count);
    return parseValue(w.toString()).get(0);
  }
  
  private void testInlineUserSerialization(LDUser user, LDValue expectedJsonValue, EventsConfiguration baseConfig) throws IOException {
    EventsConfiguration config = makeEventsConfig(baseConfig.allAttributesPrivate, true, baseConfig.privateAttributes);
    EventOutputFormatter f = new EventOutputFormatter(config);

    Event.FeatureRequest featureEvent = EventFactory.DEFAULT.newFeatureRequestEvent(
        flagBuilder("flag").build(),
        user,
        EvalResult.of(LDValue.ofNull(), NO_VARIATION, EvaluationReason.off()),
        LDValue.ofNull());
    LDValue outputEvent = getSingleOutputEvent(f, featureEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("userKey"));
    assertEquals(expectedJsonValue, outputEvent.get("user"));

    Event.Identify identifyEvent = EventFactory.DEFAULT.newIdentifyEvent(user);
    outputEvent = getSingleOutputEvent(f, identifyEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("userKey"));
    assertEquals(expectedJsonValue, outputEvent.get("user"));

    Event.Custom customEvent = EventFactory.DEFAULT.newCustomEvent("custom", user, LDValue.ofNull(), null);
    outputEvent = getSingleOutputEvent(f, customEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("userKey"));
    assertEquals(expectedJsonValue, outputEvent.get("user"));
    
    Event.Index indexEvent = new Event.Index(0, user);
    outputEvent = getSingleOutputEvent(f, indexEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("userKey"));
    assertEquals(expectedJsonValue, outputEvent.get("user"));
  }
}
