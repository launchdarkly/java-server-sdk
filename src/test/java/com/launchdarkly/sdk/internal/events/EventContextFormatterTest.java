package com.launchdarkly.sdk.internal.events;

import com.google.common.collect.ImmutableList;
import com.google.gson.stream.JsonWriter;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.internal.events.EventContextFormatter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class EventContextFormatterTest {
  private final LDContext context;
  private final boolean allAttributesPrivate;
  private final AttributeRef[] globalPrivateAttributes;
  private final String expectedJson;

  public EventContextFormatterTest(
      String name,
      LDContext context,
      boolean allAttributesPrivate,
      AttributeRef[] globalPrivateAttributes,
      String expectedJson
      ) {
    this.context = context;
    this.allAttributesPrivate = allAttributesPrivate;
    this.globalPrivateAttributes = globalPrivateAttributes;
    this.expectedJson = expectedJson;
  }
  
  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return ImmutableList.of(
        new Object[] {
            "no attributes private, single kind",
            LDContext.builder("my-key").kind("org")
              .name("my-name")
              .set("attr1", "value1")
              .build(),
            false,
            null,
            "{\"kind\": \"org\", \"key\": \"my-key\", \"name\": \"my-name\", \"attr1\": \"value1\"}"
        },
        new Object[] {
            "no attributes private, multi-kind",
            LDContext.createMulti(
                LDContext.builder("org-key").kind("org")
                  .name("org-name")
                  .build(),
                LDContext.builder("user-key")
                  .name("user-name")
                  .set("attr1", "value1")
                  .build()
                ),
            false,
            null,
            "{" +
                "\"kind\": \"multi\"," +
                "\"org\": {\"key\": \"org-key\", \"name\": \"org-name\"}," +
                "\"user\": {\"key\": \"user-key\", \"name\": \"user-name\", \"attr1\": \"value1\"}" +
            "}"
        },
        new Object[] {
            "anonymous",
            LDContext.builder("my-key").kind("org").anonymous(true).build(),
            false,
            null,
            "{\"kind\": \"org\", \"key\": \"my-key\", \"anonymous\": true}"
        },
        new Object[] {
            "secondary",
            LDContext.builder("my-key").kind("org").secondary("x").build(),
            false,
            null,
            "{\"kind\": \"org\", \"key\": \"my-key\", \"_meta\": {\"secondary\": \"x\"}}"
        },
        new Object[] {
            "all attributes private globally",
            LDContext.builder("my-key").kind("org")
              .name("my-name")
              .set("attr1", "value1")
              .build(),
            true,
            null,
            "{" +
                "\"kind\": \"org\"," +
                "\"key\": \"my-key\"," +
                "\"_meta\": {" +
                    "\"redactedAttributes\": [\"attr1\", \"name\"]" +
                "}" +
            "}"
        },
        new Object[] {
            "some top-level attributes private",
            LDContext.builder("my-key").kind("org")
              .name("my-name")
              .set("attr1", "value1")
              .set("attr2", "value2")
              .privateAttributes("attr2")
              .build(),
            false,
            new AttributeRef[] { AttributeRef.fromLiteral("name") },
            "{" +
                "\"kind\": \"org\"," +
                "\"key\": \"my-key\"," +
                "\"attr1\": \"value1\"," +
                "\"_meta\": {" +
                    "\"redactedAttributes\": [\"attr2\", \"name\"]" +
                "}" +
            "}"
        },
        new Object[] {
            "partially redacting object attributes",
            LDContext.builder("my-key")
              .set("address", LDValue.parse("{\"street\": \"17 Highbrow St.\", \"city\": \"London\"}"))
              .set("complex", LDValue.parse("{\"a\": {\"b\": {\"c\": 1, \"d\": 2}, \"e\": 3}, \"f\": 4, \"g\": 5}"))
              .privateAttributes("/complex/a/b/d", "/complex/a/b/nonexistent-prop", "/complex/f", "/complex/g/g-is-not-an-object")
              .build(),
            false,
            new AttributeRef[] { AttributeRef.fromPath("/address/street") },
            "{" +
                "\"kind\": \"user\"," +
                "\"key\": \"my-key\"," +
                "\"address\": {\"city\": \"London\"}," +
                "\"complex\": {\"a\": {\"b\": {\"c\": 1}, \"e\": 3}, \"g\": 5}," +
                "\"_meta\": {" +
                    "\"redactedAttributes\": [\"/address/street\", \"/complex/a/b/d\", \"/complex/f\"]" +
                "}" +
            "}"
        }
    );
  }
  
  @Test
  public void testOutput() throws Exception {
    EventContextFormatter f = new EventContextFormatter(allAttributesPrivate, globalPrivateAttributes);
    StringWriter sw = new StringWriter();
    JsonWriter jw = new JsonWriter(sw);
    
    f.write(context, jw);
    jw.flush();
    
    String canonicalizedOutput = canonicalizeOutputJson(sw.toString());
    assertJsonEquals(expectedJson, canonicalizedOutput);
  }
  
  private static String canonicalizeOutputJson(String json) {
    return valueWithRedactedAttributesSorted(LDValue.parse(json)).toJsonString();
  }
  
  private static LDValue valueWithRedactedAttributesSorted(LDValue value) {
    switch (value.getType()) {
    case OBJECT:
      ObjectBuilder ob = LDValue.buildObject();
      for (String key: value.keys()) {
        LDValue propValue = value.get(key);
        if (key.equals("redactedAttributes")) {
          List<String> strings = new ArrayList<>();
          for (LDValue element: propValue.values()) {
            strings.add(element.stringValue());
          }
          Collections.sort(strings);
          ob.put(key, LDValue.Convert.String.arrayFrom(strings));
        } else {
          ob.put(key, valueWithRedactedAttributesSorted(propValue));
        }
      }
      return ob.build();
    default:
      return value;
    }
  }
}
