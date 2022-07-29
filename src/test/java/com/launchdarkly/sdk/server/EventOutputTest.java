package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.ContextBuilder;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.server.Event.FeatureRequest;
import com.launchdarkly.sdk.server.EventSummarizer.EventSummary;

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.server.TestUtil.assertJsonEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EventOutputTest extends BaseEventTest {
  private static final Gson gson = new Gson();
  
  private ContextBuilder contextBuilderWithAllAttributes = LDContext.builder("userkey")
      .anonymous(true)
      .name("me")
      .secondary("s")
      .set("custom1", "value1")
      .set("custom2", "value2");
  private static final LDValue contextJsonWithAllAttributes = parseValue("{" +
      "\"kind\":\"user\"," +
      "\"key\":\"userkey\"," +
      "\"anonymous\":true," +
      "\"custom1\":\"value1\"," +
      "\"custom2\":\"value2\"," +
      "\"name\":\"me\"," +
      "\"_meta\":{\"secondary\":\"s\"}" +
      "}");
  
  @Test
  public void allAttributesAreSerialized() throws Exception {
    testInlineContextSerialization(contextBuilderWithAllAttributes.build(), contextJsonWithAllAttributes,
        defaultEventsConfig());
  }

  @Test
  public void contextKeysAreSetInsteadOfContextWhenNotInlined() throws Exception {
    testContextKeysSerialization(
        LDContext.create("userkey"),
        LDValue.buildObject().put("user", "userkey").build()
        );

    testContextKeysSerialization(
        LDContext.create(ContextKind.of("kind1"), "key1"),
        LDValue.buildObject().put("kind1", "key1").build()
        );

    testContextKeysSerialization(
        LDContext.createMulti(
            LDContext.create(ContextKind.of("kind1"), "key1"),
            LDContext.create(ContextKind.of("kind2"), "key2")),
        LDValue.buildObject().put("kind1", "key1").put("kind2", "key2").build()
        );
  }

  @Test
  public void allAttributesPrivateMakesAttributesPrivate() throws Exception {
    // We test this behavior in more detail in EventContextFormatterTest, but here we're verifying that the
    // EventOutputFormatter is actually using EventContextFormatter and configuring it correctly.
    LDContext context = LDContext.builder("userkey")
      .name("me")
      .build();
    LDValue expectedJson = LDValue.buildObject()
        .put("kind", "user")
        .put("key", context.getKey())
        .put("_meta", LDValue.parse("{\"redactedAttributes\":[\"name\"]}"))
        .build();
    EventsConfiguration config = makeEventsConfig(true, null);
    testInlineContextSerialization(context, expectedJson, config);
  }

  @Test
  public void globalPrivateAttributeNamesMakeAttributesPrivate() throws Exception {
    // See comment in allAttributesPrivateMakesAttributesPrivate
    LDContext context = LDContext.builder("userkey")
        .name("me")
        .set("attr1", "value1")
        .build();
    LDValue expectedJson = LDValue.buildObject()
        .put("kind", "user")
        .put("key", context.getKey())
        .put("name", "me")
        .put("_meta", LDValue.parse("{\"redactedAttributes\":[\"attr1\"]}"))
        .build();
    EventsConfiguration config = makeEventsConfig(false, ImmutableSet.of(AttributeRef.fromLiteral("attr1")));
    testInlineContextSerialization(context, expectedJson, config);
  }
  
  @Test
  public void perContextPrivateAttributesMakeAttributePrivate() throws Exception {
    // See comment in allAttributesPrivateMakesAttributesPrivate
    LDContext context = LDContext.builder("userkey")
        .name("me")
        .set("attr1", "value1")
        .privateAttributes("attr1")
        .build();
    LDValue expectedJson = LDValue.buildObject()
        .put("kind", "user")
        .put("key", context.getKey())
        .put("name", "me")
        .put("_meta", LDValue.parse("{\"redactedAttributes\":[\"attr1\"]}"))
        .build();
    EventsConfiguration config = makeEventsConfig(false, null);
    testInlineContextSerialization(context, expectedJson, config);
  }
  
  private ObjectBuilder buildFeatureEventProps(String key, String userKey) {
    return LDValue.buildObject()
        .put("kind", "feature")
        .put("key", key)
        .put("creationDate", 100000)
        .put("contextKeys", LDValue.buildObject().put("user", userKey).build());
  }

  private ObjectBuilder buildFeatureEventProps(String key) {
    return buildFeatureEventProps(key, "userkey");
  }
  
  @Test
  public void featureEventIsSerialized() throws Exception {
    LDContext context = LDContext.builder("userkey").name("me").build();
    LDValue value = LDValue.of("flagvalue"), defaultVal = LDValue.of("defaultvalue");
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());
    
    FeatureRequest feWithVariation = featureEvent(context, FLAG_KEY).flagVersion(FLAG_VERSION).variation(1)
        .value(value).defaultValue(defaultVal).build();
    LDValue feJson1 = buildFeatureEventProps(FLAG_KEY)
        .put("version", FLAG_VERSION)
        .put("variation", 1)
        .put("value", value)
        .put("default", defaultVal)
        .build();
    assertJsonEquals(feJson1, getSingleOutputEvent(f, feWithVariation));

    FeatureRequest feWithoutVariationOrDefault = featureEvent(context, FLAG_KEY).flagVersion(FLAG_VERSION)
        .variation(NO_VARIATION).value(value).defaultValue(null).build();
    LDValue feJson2 = buildFeatureEventProps(FLAG_KEY)
        .put("version", FLAG_VERSION)
        .put("value", value)
        .build();
    assertJsonEquals(feJson2, getSingleOutputEvent(f, feWithoutVariationOrDefault));

    FeatureRequest feWithReason = featureEvent(context, FLAG_KEY).flagVersion(FLAG_VERSION).variation(1)
        .value(value).defaultValue(defaultVal).reason(EvaluationReason.fallthrough()).build();
    LDValue feJson3 = buildFeatureEventProps(FLAG_KEY)
        .put("version", FLAG_VERSION)
        .put("variation", 1)
        .put("value", value)
        .put("default", defaultVal)
        .put("reason", LDValue.buildObject().put("kind", "FALLTHROUGH").build())
        .build();
    assertJsonEquals(feJson3, getSingleOutputEvent(f, feWithReason));

    Event.FeatureRequest debugEvent = feWithVariation.toDebugEvent();
    LDValue feJson5 = LDValue.buildObject()
        .put("kind", "debug")
        .put("key", FLAG_KEY)
        .put("creationDate", 100000)
        .put("version", FLAG_VERSION)
        .put("variation", 1)
        .put("context", LDValue.buildObject().put("kind", "user").put("key", "userkey").put("name", "me").build())
        .put("value", value)
        .put("default", defaultVal)
        .build();
    assertJsonEquals(feJson5, getSingleOutputEvent(f, debugEvent));
    
    Event.FeatureRequest prereqEvent = featureEvent(context, FLAG_KEY).flagVersion(FLAG_VERSION)
        .variation(1).value(value).defaultValue(null).prereqOf("parent").build();
    LDValue feJson6 = buildFeatureEventProps(FLAG_KEY)
        .put("version", 11)
        .put("variation", 1)
        .put("value", "flagvalue")
        .put("prereqOf", "parent")
        .build();
    assertJsonEquals(feJson6, getSingleOutputEvent(f, prereqEvent));
  }

  @Test
  public void identifyEventIsSerialized() throws IOException {
    LDContext context = LDContext.builder("userkey").name("me").build();
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());

    Event.Identify ie = identifyEvent(context);
    LDValue ieJson = parseValue("{" +
        "\"kind\":\"identify\"," +
        "\"creationDate\":100000," +
        "\"context\":{\"kind\":\"user\",\"key\":\"userkey\",\"name\":\"me\"}" +
        "}");
    assertJsonEquals(ieJson, getSingleOutputEvent(f, ie));
  }

  @Test
  public void customEventIsSerialized() throws IOException {
    LDContext context = LDContext.builder("userkey").name("me").build();
    LDValue contextKeysJson = LDValue.buildObject().put("user", context.getKey()).build();
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());

    Event.Custom ceWithoutData = customEvent(context, "customkey").build();
    LDValue ceJson1 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"contextKeys\":" + contextKeysJson +
        "}");
    assertJsonEquals(ceJson1, getSingleOutputEvent(f, ceWithoutData));

    Event.Custom ceWithData = customEvent(context, "customkey").data(LDValue.of("thing")).build();
    LDValue ceJson2 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"contextKeys\":" + contextKeysJson + "," +
        "\"data\":\"thing\"" +
        "}");
    assertJsonEquals(ceJson2, getSingleOutputEvent(f, ceWithData));

    Event.Custom ceWithMetric = customEvent(context, "customkey").metricValue(2.5).build();
    LDValue ceJson3 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"contextKeys\":" + contextKeysJson + "," +
        "\"metricValue\":2.5" +
        "}");
    assertJsonEquals(ceJson3, getSingleOutputEvent(f, ceWithMetric));

    Event.Custom ceWithDataAndMetric = customEvent(context, "customkey").data(LDValue.of("thing"))
        .metricValue(2.5).build();
    LDValue ceJson4 = parseValue("{" +
        "\"kind\":\"custom\"," +
        "\"creationDate\":100000," +
        "\"key\":\"customkey\"," +
        "\"contextKeys\":" + contextKeysJson + "," +
        "\"data\":\"thing\"," +
        "\"metricValue\":2.5" +
        "}");
    assertJsonEquals(ceJson4, getSingleOutputEvent(f, ceWithDataAndMetric));
  }

  @Test
  public void summaryEventIsSerialized() throws Exception {
    LDValue value1a = LDValue.of("value1a"), value2a = LDValue.of("value2a"), value2b = LDValue.of("value2b"),
        default1 = LDValue.of("default1"), default2 = LDValue.of("default2"), default3 = LDValue.of("default3");
    LDContext context1 = LDContext.create("key1");
    LDContext context2 = LDContext.createMulti(context1, LDContext.create(ContextKind.of("kind2"), "key2"));
    
    EventSummarizer es = new EventSummarizer();
    
    es.summarizeEvent(1000, "first", 11, 1, value1a, default1, context1); // context1 has kind "user"
    
    es.summarizeEvent(1000, "second", 21, 1, value2a, default2, context1);

    es.summarizeEvent(1001, "first", 11, 1, value1a, default1, context1);
    es.summarizeEvent(1001, "first", 12, 1, value1a, default1, context2); // context2 has kind "user" and kind "kind2"

    es.summarizeEvent(1001, "second", 21, 2, value2b, default2, context1);
    es.summarizeEvent(1002, "second", 21, -1, default2, default2, context1);

    es.summarizeEvent(1002, "third", -1, -1, default3, default3, context1);

    EventSummary summary = es.getSummaryAndReset();

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
    assertThat(firstJson.get("contextKinds").values(), containsInAnyOrder(
        LDValue.of("user"), LDValue.of("kind2")));
    assertThat(firstJson.get("counters").values(), containsInAnyOrder(
        parseValue("{\"value\":\"value1a\",\"variation\":1,\"version\":11,\"count\":2}"),
        parseValue("{\"value\":\"value1a\",\"variation\":1,\"version\":12,\"count\":1}")
    ));

    LDValue secondJson = featuresJson.get("second");
    assertEquals("default2", secondJson.get("default").stringValue());
    assertThat(secondJson.get("contextKinds").values(), contains(LDValue.of("user")));
    assertThat(secondJson.get("counters").values(), containsInAnyOrder(
            parseValue("{\"value\":\"value2a\",\"variation\":1,\"version\":21,\"count\":1}"),
            parseValue("{\"value\":\"value2b\",\"variation\":2,\"version\":21,\"count\":1}"),
            parseValue("{\"value\":\"default2\",\"version\":21,\"count\":1}")
        ));
        
    LDValue thirdJson = featuresJson.get("third");
    assertEquals("default3", thirdJson.get("default").stringValue());
    assertThat(thirdJson.get("contextKinds").values(), contains(LDValue.of("user")));
    assertThat(thirdJson.get("counters").values(), contains(
        parseValue("{\"unknown\":true,\"value\":\"default3\",\"count\":1}")
    ));
  }
  
  @Test
  public void unknownEventClassIsNotSerialized() throws Exception {
    // This shouldn't be able to happen in reality.
    Event event = new FakeEventClass(1000, LDContext.create("user"));
    
    EventOutputFormatter f = new EventOutputFormatter(defaultEventsConfig());
    StringWriter w = new StringWriter();
    f.writeOutputEvents(new Event[] { event }, new EventSummary(), w);
    
    assertEquals("[]", w.toString());
  }
  
  private static class FakeEventClass extends Event {
    public FakeEventClass(long creationDate, LDContext context) {
      super(creationDate, context);
    }
  }
  
  private static LDValue parseValue(String json) {
    return gson.fromJson(json, LDValue.class);
  }

  private LDValue getSingleOutputEvent(EventOutputFormatter f, Event event) throws IOException {
    StringWriter w = new StringWriter();
    int count = f.writeOutputEvents(new Event[] { event }, new EventSummary(), w);
    assertEquals(1, count);
    return parseValue(w.toString()).get(0);
  }
  
  private void testContextKeysSerialization(LDContext context, LDValue expectedJsonValue) throws IOException {
    EventsConfiguration config = makeEventsConfig(false, null);
    EventOutputFormatter f = new EventOutputFormatter(config);
    
    Event.FeatureRequest featureEvent = featureEvent(context, FLAG_KEY).build();
    LDValue outputEvent = getSingleOutputEvent(f, featureEvent);
    assertJsonEquals(expectedJsonValue, outputEvent.get("contextKeys"));
    assertJsonEquals(LDValue.ofNull(), outputEvent.get("context"));
    
    Event.Custom customEvent = customEvent(context, "eventkey").build();
    outputEvent = getSingleOutputEvent(f, customEvent);
    assertJsonEquals(expectedJsonValue, outputEvent.get("contextKeys"));
    assertJsonEquals(LDValue.ofNull(), outputEvent.get("context"));
  }
  
  private void testInlineContextSerialization(LDContext context, LDValue expectedJsonValue, EventsConfiguration baseConfig) throws IOException {
    EventsConfiguration config = makeEventsConfig(baseConfig.allAttributesPrivate, baseConfig.privateAttributes);
    EventOutputFormatter f = new EventOutputFormatter(config);

    Event.Identify identifyEvent = identifyEvent(context);
    LDValue outputEvent = getSingleOutputEvent(f, identifyEvent);
    assertJsonEquals(LDValue.ofNull(), outputEvent.get("contextKeys"));
    assertJsonEquals(expectedJsonValue, outputEvent.get("context"));
    
    Event.Index indexEvent = new Event.Index(0, context);
    outputEvent = getSingleOutputEvent(f, indexEvent);
    assertJsonEquals(LDValue.ofNull(), outputEvent.get("contextKeys"));
    assertJsonEquals(expectedJsonValue, outputEvent.get("context"));
  }
}
