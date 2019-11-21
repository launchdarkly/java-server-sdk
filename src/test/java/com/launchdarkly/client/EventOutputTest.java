package com.launchdarkly.client;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.launchdarkly.client.Event.FeatureRequest;
import com.launchdarkly.client.EventSummarizer.EventSummary;
import com.launchdarkly.client.value.LDValue;
import com.launchdarkly.client.value.ObjectBuilder;

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;

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
  private LDValue userJsonWithAllAttributes = parseValue("{" +
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
        new LDConfig.Builder());
  }

  @Test
  public void unsetUserAttributesAreNotSerialized() throws Exception {
      LDUser user = new LDUser("userkey");
      LDValue userJson = parseValue("{\"key\":\"userkey\"}");
      testInlineUserSerialization(user, userJson, new LDConfig.Builder());
  }

  @Test
  public void userKeyIsSetInsteadOfUserWhenNotInlined() throws Exception {
    LDUser user = new LDUser.Builder("userkey").name("me").build();
    LDValue userJson = parseValue("{\"key\":\"userkey\",\"name\":\"me\"}");
    EventOutputFormatter f = new EventOutputFormatter(new LDConfig.Builder().build());

    Event.FeatureRequest featureEvent = EventFactory.DEFAULT.newFeatureRequestEvent(
        new FeatureFlagBuilder("flag").build(),
        user,
        new EvaluationDetail<LDValue>(EvaluationReason.off(), null, LDValue.ofNull()),
        LDValue.ofNull());
    LDValue outputEvent = getSingleOutputEvent(f, featureEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("user"));
    assertEquals(user.getKey(), outputEvent.get("userKey"));

    Event.Identify identifyEvent = EventFactory.DEFAULT.newIdentifyEvent(user);
    outputEvent = getSingleOutputEvent(f, identifyEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("userKey"));
    assertEquals(userJson, outputEvent.get("user"));

    Event.Custom customEvent = EventFactory.DEFAULT.newCustomEvent("custom", user, LDValue.ofNull(), null);
    outputEvent = getSingleOutputEvent(f, customEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("user"));
    assertEquals(user.getKey(), outputEvent.get("userKey"));
    
    Event.Index indexEvent = new Event.Index(0, user);
    outputEvent = getSingleOutputEvent(f, indexEvent);
    assertEquals(LDValue.ofNull(), outputEvent.get("userKey"));
    assertEquals(userJson, outputEvent.get("user"));
  }

  @Test
  public void allAttributesPrivateMakesAttributesPrivate() throws Exception {
    LDUser user = userBuilderWithAllAttributes.build();
    LDConfig config = new LDConfig.Builder().allAttributesPrivate(true).build();
    testPrivateAttributes(config, user, attributesThatCanBePrivate);
  }

  @Test
  public void globalPrivateAttributeNamesMakeAttributesPrivate() throws Exception {
    LDUser user = userBuilderWithAllAttributes.build();
    for (String attrName: attributesThatCanBePrivate) {
      LDConfig config = new LDConfig.Builder().privateAttributeNames(attrName).build();
      testPrivateAttributes(config, user, attrName);
    }
  }
  
  @Test
  public void perUserPrivateAttributesMakeAttributePrivate() throws Exception {
    LDUser baseUser = userBuilderWithAllAttributes.build();
    LDConfig config = new LDConfig.Builder().build();
    
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateAvatar("x").build(), "avatar");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateCountry("US").build(), "country");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateCustom("custom1", "x").build(), "custom1");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateEmail("x").build(), "email");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateFirstName("x").build(), "firstName");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateLastName("x").build(), "lastName");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateName("x").build(), "name");
    testPrivateAttributes(config, new LDUser.Builder(baseUser).privateSecondary("x").build(), "secondary");
  }
  
  private void testPrivateAttributes(LDConfig config, LDUser user, String... privateAttrNames) throws IOException {
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
  
  @Test
  public void featureEventIsSerialized() throws Exception {
    EventFactory factory = eventFactoryWithTimestamp(100000, false);
    EventFactory factoryWithReason = eventFactoryWithTimestamp(100000, true);
    FeatureFlag flag = new FeatureFlagBuilder("flag").version(11).build();
    LDUser user = new LDUser.Builder("userkey").name("me").build();
    EventOutputFormatter f = new EventOutputFormatter(LDConfig.DEFAULT);
    
    FeatureRequest feWithVariation = factory.newFeatureRequestEvent(flag, user,
        new EvaluationDetail<LDValue>(EvaluationReason.off(), 1, LDValue.of("flagvalue")),
        LDValue.of("defaultvalue"));
    LDValue feJson1 = parseValue("{" +
        "\"kind\":\"feature\"," +
        "\"creationDate\":100000," +
        "\"key\":\"flag\"," +
        "\"version\":11," +
        "\"userKey\":\"userkey\"," +
        "\"value\":\"flagvalue\"," +
        "\"variation\":1," +
        "\"default\":\"defaultvalue\"" +
        "}");
    assertEquals(feJson1, getSingleOutputEvent(f, feWithVariation));

    FeatureRequest feWithoutVariationOrDefault = factory.newFeatureRequestEvent(flag, user,
        new EvaluationDetail<LDValue>(EvaluationReason.off(), null, LDValue.of("flagvalue")),
        LDValue.ofNull());
    LDValue feJson2 = parseValue("{" +
        "\"kind\":\"feature\"," +
        "\"creationDate\":100000," +
        "\"key\":\"flag\"," +
        "\"version\":11," +
        "\"userKey\":\"userkey\"," +
        "\"value\":\"flagvalue\"" +
        "}");
    assertEquals(feJson2, getSingleOutputEvent(f, feWithoutVariationOrDefault));

    FeatureRequest feWithReason = factoryWithReason.newFeatureRequestEvent(flag, user,
        new EvaluationDetail<LDValue>(EvaluationReason.ruleMatch(1, "id"), 1, LDValue.of("flagvalue")),
        LDValue.of("defaultvalue"));
    LDValue feJson3 = parseValue("{" +
        "\"kind\":\"feature\"," +
        "\"creationDate\":100000," +
        "\"key\":\"flag\"," +
        "\"version\":11," +
        "\"userKey\":\"userkey\"," +
        "\"value\":\"flagvalue\"," +
        "\"variation\":1," +
        "\"default\":\"defaultvalue\"," +
        "\"reason\":{\"kind\":\"RULE_MATCH\",\"ruleIndex\":1,\"ruleId\":\"id\"}" +
        "}");
    assertEquals(feJson3, getSingleOutputEvent(f, feWithReason));

    FeatureRequest feUnknownFlag = factoryWithReason.newUnknownFeatureRequestEvent("flag", user,
        LDValue.of("defaultvalue"), EvaluationReason.ErrorKind.FLAG_NOT_FOUND);
    LDValue feJson4 = parseValue("{" +
        "\"kind\":\"feature\"," +
        "\"creationDate\":100000," +
        "\"key\":\"flag\"," +
        "\"userKey\":\"userkey\"," +
        "\"value\":\"defaultvalue\"," +
        "\"default\":\"defaultvalue\"," +
        "\"reason\":{\"kind\":\"ERROR\",\"errorKind\":\"FLAG_NOT_FOUND\"}" +
        "}");
    assertEquals(feJson4, getSingleOutputEvent(f, feUnknownFlag));

    Event.FeatureRequest debugEvent = factory.newDebugEvent(feWithVariation);
    LDValue feJson5 = parseValue("{" +
        "\"kind\":\"debug\"," +
        "\"creationDate\":100000," +
        "\"key\":\"flag\"," +
        "\"version\":11," +
        "\"user\":{\"key\":\"userkey\",\"name\":\"me\"}," +
        "\"value\":\"flagvalue\"," +
        "\"variation\":1," +
        "\"default\":\"defaultvalue\"" +
        "}");
    assertEquals(feJson5, getSingleOutputEvent(f, debugEvent));
  }

  @Test
  public void identifyEventIsSerialized() throws IOException {
    EventFactory factory = eventFactoryWithTimestamp(100000, false);
    LDUser user = new LDUser.Builder("userkey").name("me").build();
    EventOutputFormatter f = new EventOutputFormatter(LDConfig.DEFAULT);

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
    EventOutputFormatter f = new EventOutputFormatter(LDConfig.DEFAULT);

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
    summary.incrementCounter(new String("second"), null, 21, LDValue.of("default2"), LDValue.of("default2")); // flag exists (has version), but eval failed (no variation)

    summary.incrementCounter(new String("third"), null, null, LDValue.of("default3"), LDValue.of("default3")); // flag doesn't exist (no version)

    summary.noteTimestamp(1000);
    summary.noteTimestamp(1002);

    EventOutputFormatter f = new EventOutputFormatter(new LDConfig.Builder().build());
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
  
  private LDValue parseValue(String json) {
    return gson.fromJson(json, LDValue.class);
  }

  private EventFactory eventFactoryWithTimestamp(final long timestamp, final boolean includeReasons) {
    return new EventFactory() {
      protected long getTimestamp() {
        return timestamp;
      }
      
      protected boolean isIncludeReasons() {
        return includeReasons;
      }
    };
  }
  
  private LDValue getSingleOutputEvent(EventOutputFormatter f, Event event) throws IOException {
    StringWriter w = new StringWriter();
    int count = f.writeOutputEvents(new Event[] { event }, new EventSummary(), w);
    assertEquals(1, count);
    return parseValue(w.toString()).get(0);
  }
  
  private void testInlineUserSerialization(LDUser user, LDValue expectedJsonValue, LDConfig.Builder baseConfig) throws IOException {
    baseConfig.inlineUsersInEvents(true);
    EventOutputFormatter f = new EventOutputFormatter(baseConfig.build());

    Event.FeatureRequest featureEvent = EventFactory.DEFAULT.newFeatureRequestEvent(
        new FeatureFlagBuilder("flag").build(),
        user,
        new EvaluationDetail<LDValue>(EvaluationReason.off(), null, LDValue.ofNull()),
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
