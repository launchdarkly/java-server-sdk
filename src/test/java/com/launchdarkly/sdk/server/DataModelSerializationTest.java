package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Operator;
import com.launchdarkly.sdk.server.DataModel.Prerequisite;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.Rule;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;
import com.launchdarkly.sdk.server.DataModel.Target;
import com.launchdarkly.sdk.server.DataModel.VersionedData;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.interfaces.SerializationException;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.DataKind;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.interfaces.DataStoreTypes.ItemDescriptor;

import org.junit.Test;

import java.util.Collections;
import java.util.function.Consumer;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.DataModelSerialization.deserializeFromParsedJson;
import static com.launchdarkly.sdk.server.DataModelSerialization.parseFullDataSet;
import static com.launchdarkly.sdk.server.JsonHelpers.serialize;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.TestUtil.assertDataSetEquals;
import static com.launchdarkly.sdk.server.TestUtil.jsonReaderFrom;
import static com.launchdarkly.testhelpers.JsonAssertions.assertJsonEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class DataModelSerializationTest {

  @Test
  public void deserializeFlagFromParsedJson() {
    String json = "{\"key\":\"flagkey\",\"version\":1}";
    JsonElement element = JsonHelpers.gsonInstance().fromJson(json, JsonElement.class);
    VersionedData flag = deserializeFromParsedJson(DataModel.FEATURES, element);
    assertEquals(FeatureFlag.class, flag.getClass());
    assertEquals("flagkey", flag.getKey());
    assertEquals(1, flag.getVersion());
  }

  @Test(expected=SerializationException.class)
  public void deserializeInvalidFlagFromParsedJson() {
    String json = "{\"key\":[3]}";
    JsonElement element = JsonHelpers.gsonInstance().fromJson(json, JsonElement.class);
    deserializeFromParsedJson(DataModel.FEATURES, element);
  }

  @Test
  public void deserializeSegmentFromParsedJson() {
    String json = "{\"key\":\"segkey\",\"version\":1}";
    JsonElement element = JsonHelpers.gsonInstance().fromJson(json, JsonElement.class);
    VersionedData segment = deserializeFromParsedJson(DataModel.SEGMENTS, element);
    assertEquals(Segment.class, segment.getClass());
    assertEquals("segkey", segment.getKey());
    assertEquals(1, segment.getVersion());
  }

  @Test(expected=SerializationException.class)
  public void deserializeInvalidSegmentFromParsedJson() {
    String json = "{\"key\":[3]}";
    JsonElement element = JsonHelpers.gsonInstance().fromJson(json, JsonElement.class);
    deserializeFromParsedJson(DataModel.SEGMENTS, element);
  }

  @Test(expected=IllegalArgumentException.class)
  public void deserializeInvalidDataKindFromParsedJson() {
    String json = "{\"key\":\"something\",\"version\":1}";
    JsonElement element = JsonHelpers.gsonInstance().fromJson(json, JsonElement.class);
    DataKind mysteryKind = new DataKind("incorrect", null, null);
    deserializeFromParsedJson(mysteryKind, element);
  }

  @Test
  public void flagIsDeserializedWithAllProperties() {
    assertFlagFromJson(
        flagWithAllPropertiesJson(),
        flag -> {
          assertFlagHasAllProperties(flag);

          String json1 = FEATURES.serialize(new ItemDescriptor(flag.getVersion(), flag));
          assertFlagFromJson(LDValue.parse(json1), flag1 -> assertFlagHasAllProperties(flag1));
        });
  }
  
  @Test
  public void flagIsDeserializedWithMinimalProperties() {
    assertFlagFromJson(
        LDValue.buildObject().put("key", "flag-key").put("version", 99).build(),
        flag -> {
          assertEquals("flag-key", flag.getKey());
          assertEquals(99, flag.getVersion());
          assertFalse(flag.isOn());
          assertNull(flag.getSalt());    
          assertNotNull(flag.getTargets());
          assertEquals(0, flag.getTargets().size());
          assertNotNull(flag.getRules());
          assertEquals(0, flag.getRules().size());
          assertNull(flag.getFallthrough());
          assertNull(flag.getOffVariation());
          assertNotNull(flag.getVariations());
          assertEquals(0, flag.getVariations().size());
          assertFalse(flag.isClientSide());
          assertFalse(flag.isTrackEvents());
          assertFalse(flag.isTrackEventsFallthrough());
          assertNull(flag.getDebugEventsUntilDate());
        });
  }
  
  @Test
  public void flagIsDeserializedWithOptionalExperimentProperties() {
    String json = LDValue.buildObject()
    .put("key", "flag-key")
    .put("version", 157)
    .put("rules", LDValue.buildArray()
        .add(LDValue.buildObject()
            .put("id", "id1")
            .put("rollout", LDValue.buildObject()
                .put("variations", LDValue.buildArray()
                    .add(LDValue.buildObject()
                        .put("variation", 2)
                        .put("weight", 100000)
                        .build())
                    .build())
                .put("bucketBy", "email")
                .build())
            .build())
        .build())
    .put("fallthrough", LDValue.buildObject()
        .put("variation", 1)
        .build())
    .put("offVariation", 2)
    .put("variations", LDValue.buildArray().add("a").add("b").add("c").build())
    .build().toJsonString();
    FeatureFlag flag = (FeatureFlag)FEATURES.deserialize(json).getItem();
    assertEquals("flag-key", flag.getKey());
    assertEquals(157, flag.getVersion());
    assertFalse(flag.isOn());
    assertNull(flag.getSalt());    
    assertNotNull(flag.getTargets());
    assertEquals(0, flag.getTargets().size());
    assertNotNull(flag.getRules());
    assertEquals(1, flag.getRules().size());
    assertNull(flag.getRules().get(0).getRollout().getKind());
    assertFalse(flag.getRules().get(0).getRollout().isExperiment());
    assertNull(flag.getRules().get(0).getRollout().getSeed());
    assertEquals(2, flag.getRules().get(0).getRollout().getVariations().get(0).getVariation());
    assertEquals(100000, flag.getRules().get(0).getRollout().getVariations().get(0).getWeight());
    assertFalse(flag.getRules().get(0).getRollout().getVariations().get(0).isUntracked());
    assertNotNull(flag.getVariations());
    assertEquals(3, flag.getVariations().size());
    assertFalse(flag.isClientSide());
    assertFalse(flag.isTrackEvents());
    assertFalse(flag.isTrackEventsFallthrough());
    assertNull(flag.getDebugEventsUntilDate());
  }
  
  @Test
  public void deletedFlagIsConvertedToAndFromJsonPlaceholder() {
    String json0 = LDValue.buildObject().put("version", 99)
        .put("deleted", true).build().toJsonString();
    ItemDescriptor item = FEATURES.deserialize(json0);
    assertNotNull(item);
    assertNull(item.getItem());
    assertEquals(99, item.getVersion());
    
    String json1 = FEATURES.serialize(item);
    assertJsonEquals(json0, json1);
  }
  
  @Test
  public void segmentIsDeserializedWithAllProperties() {
    String json0 = segmentWithAllPropertiesJson().toJsonString();
    Segment segment0 = (Segment)SEGMENTS.deserialize(json0).getItem();
    assertSegmentHasAllProperties(segment0);
    
    String json1 = SEGMENTS.serialize(new ItemDescriptor(segment0.getVersion(), segment0));
    Segment segment1 = (Segment)SEGMENTS.deserialize(json1).getItem();
    assertSegmentHasAllProperties(segment1);
  }
  
  @Test
  public void segmentIsDeserializedWithMinimalProperties() {
    String json = LDValue.buildObject().put("key", "segment-key").put("version", 99).build().toJsonString();
    Segment segment = (Segment)SEGMENTS.deserialize(json).getItem();
    assertEquals("segment-key", segment.getKey());
    assertEquals(99, segment.getVersion());
    assertNotNull(segment.getIncluded());
    assertEquals(0, segment.getIncluded().size());
    assertNotNull(segment.getExcluded());
    assertEquals(0, segment.getExcluded().size());
    assertNotNull(segment.getRules());
    assertEquals(0, segment.getRules().size());
    assertFalse(segment.isUnbounded());
    assertNull(segment.getGeneration());
  }

  @Test
  public void deletedSegmentIsConvertedToAndFromJsonPlaceholder() {
    String json0 = LDValue.buildObject().put("version", 99)
        .put("deleted", true).build().toJsonString();
    ItemDescriptor item = SEGMENTS.deserialize(json0);
    assertNotNull(item);
    assertNull(item.getItem());
    assertEquals(99, item.getVersion());
    
    String json1 = SEGMENTS.serialize(item);
    assertJsonEquals(json0, json1);
  }
  
  @Test
  public void explicitNullsAreToleratedForNullableValues() {
    // Nulls are not *always* valid-- it is OK to raise a deserialization error if a null appears
    // where a non-nullable primitive type like boolean is expected, so for instance "version":null
    // is invalid. But for anything that is optional, an explicit null is equivalent to omitting
    // the property. Note: it would be nice to use Optional<T> for things like this, but we can't
    // do it because Gson does not play well with Optional.
    assertFlagFromJson(
        baseBuilder("flag-key").put("offVariation", LDValue.ofNull()).build(),
        flag -> assertNull(flag.getOffVariation())
        );
    assertFlagFromJson(
        baseBuilder("flag-key")
          .put("fallthrough", LDValue.buildObject().put("rollout", LDValue.ofNull()).build())
          .build(),
        flag -> assertNull(flag.getFallthrough().getRollout())
        );
    assertFlagFromJson(
        baseBuilder("flag-key")
          .put("fallthrough", LDValue.buildObject().put("variation", LDValue.ofNull()).build())
          .build(),
        flag -> assertNull(flag.getFallthrough().getVariation())
        );

    // Nulls for list values should always be considered equivalent to an empty list, because
    // that's how Go would serialize a nil slice
    assertFlagFromJson(
        baseBuilder("flag-key").put("prerequisites", LDValue.ofNull()).build(),
        flag -> assertEquals(Collections.<Prerequisite>emptyList(), flag.getPrerequisites())
        );
    assertFlagFromJson(
        baseBuilder("flag-key").put("rules", LDValue.ofNull()).build(),
        flag -> assertEquals(Collections.<Rule>emptyList(), flag.getRules())
        );
    assertFlagFromJson(
        baseBuilder("flag-key").put("targets", LDValue.ofNull()).build(),
        flag -> assertEquals(Collections.<Target>emptyList(), flag.getTargets())
        );
    assertFlagFromJson(
        baseBuilder("flag-key")
          .put("rules", LDValue.arrayOf(
              LDValue.buildObject().put("clauses", LDValue.ofNull()).build()
              ))
          .build(),
        flag -> assertEquals(Collections.<Clause>emptyList(), flag.getRules().get(0).getClauses())
        );
    assertFlagFromJson(
        baseBuilder("flag-key")
          .put("rules", LDValue.arrayOf(
              LDValue.buildObject().put("clauses", LDValue.arrayOf(
                  LDValue.buildObject().put("values", LDValue.ofNull()).build()
                  )).build()
              ))
          .build(),
        flag -> assertEquals(Collections.<LDValue>emptyList(),
            flag.getRules().get(0).getClauses().get(0).getValues())
        );
    assertFlagFromJson(
        baseBuilder("flag-key")
          .put("targets", LDValue.arrayOf(
              LDValue.buildObject().put("values", LDValue.ofNull()).build()
              ))
          .build(),
        flag -> assertEquals(Collections.<String>emptySet(), flag.getTargets().get(0).getValues())
        );
    assertFlagFromJson(
        baseBuilder("flag-key")
          .put("fallthrough", LDValue.buildObject().put("rollout",
              LDValue.buildObject().put("variations", LDValue.ofNull()).build()
             ).build())
          .build(),
        flag -> assertEquals(Collections.<WeightedVariation>emptyList(),
            flag.getFallthrough().getRollout().getVariations())
        );
    assertSegmentFromJson(
        baseBuilder("segment-key").put("rules", LDValue.ofNull()).build(),
        segment -> assertEquals(Collections.<SegmentRule>emptyList(), segment.getRules())
        );
    assertSegmentFromJson(
        baseBuilder("segment-key")
          .put("rules", LDValue.arrayOf(
              LDValue.buildObject().put("clauses", LDValue.ofNull()).build()
              ))
          .build(),
        segment -> assertEquals(Collections.<Clause>emptyList(), segment.getRules().get(0).getClauses())
        );
    assertSegmentFromJson(
        baseBuilder("segment-key").put("generation", LDValue.ofNull()).build(),
        segment -> assertNull(segment.getGeneration())
    );
    
    // Nulls in clause values are not useful since the clause can never match, but they're valid JSON;
    // we should normalize them to LDValue.ofNull() to avoid potential NPEs down the line
    assertFlagFromJson(
        baseBuilder("flag-key")
          .put("rules", LDValue.arrayOf(
              LDValue.buildObject()
                .put("clauses", LDValue.arrayOf(
                    LDValue.buildObject()
                      .put("values", LDValue.arrayOf(LDValue.ofNull()))
                      .build()
                    ))
                .build()
              ))
          .build(),
        flag -> assertEquals(LDValue.ofNull(),
            flag.getRules().get(0).getClauses().get(0).getValues().get(0))
        );
    assertSegmentFromJson(
        baseBuilder("segment-key")
          .put("rules", LDValue.arrayOf(
              LDValue.buildObject()
                .put("clauses", LDValue.arrayOf(
                    LDValue.buildObject()
                      .put("values", LDValue.arrayOf(LDValue.ofNull()))
                      .build()
                    ))
                .build()
              ))
          .build(),
          segment -> assertEquals(LDValue.ofNull(),
              segment.getRules().get(0).getClauses().get(0).getValues().get(0))
          );

    // Similarly, null for a flag variation isn't a useful value but it is valid JSON
    assertFlagFromJson(
        baseBuilder("flagKey").put("variations", LDValue.arrayOf(LDValue.ofNull())).build(),
        flag -> assertEquals(LDValue.ofNull(), flag.getVariations().get(0))
        );
  }
  
  @Test
  public void parsingFullDataSetEmptyObject() throws Exception {
    String json = "{}";
    FullDataSet<ItemDescriptor> allData = parseFullDataSet(jsonReaderFrom(json));
    assertDataSetEquals(DataBuilder.forStandardTypes().build(), allData);
  }
  
  @Test
  public void parsingFullDataSetFlagsOnly() throws Exception {
    FeatureFlag flag = flagBuilder("flag1").version(1000).build();
    String json = "{\"flags\":{\"flag1\":" + serialize(flag) + "}}";
    FullDataSet<ItemDescriptor> allData = parseFullDataSet(jsonReaderFrom(json));
    assertDataSetEquals(DataBuilder.forStandardTypes().addAny(FEATURES, flag).build(), allData);
  }
  
  @Test
  public void parsingFullDataSetSegmentsOnly() throws Exception {
    Segment segment = segmentBuilder("segment1").version(1000).build();
    String json = "{\"segments\":{\"segment1\":" + serialize(segment) + "}}";
    FullDataSet<ItemDescriptor> allData = parseFullDataSet(jsonReaderFrom(json));
    assertDataSetEquals(DataBuilder.forStandardTypes().addAny(SEGMENTS, segment).build(), allData);
  }
  
  @Test
  public void parsingFullDataSetFlagsAndSegments() throws Exception {
    FeatureFlag flag1 = flagBuilder("flag1").version(1000).build();
    FeatureFlag flag2 = flagBuilder("flag2").version(1001).build();
    Segment segment1 = segmentBuilder("segment1").version(1000).build();
    Segment segment2 = segmentBuilder("segment2").version(1001).build();
    String json = "{\"flags\":{\"flag1\":" + serialize(flag1) + ",\"flag2\":" + serialize(flag2) + "}" +
        ",\"segments\":{\"segment1\":" + serialize(segment1) + ",\"segment2\":" + serialize(segment2) + "}}";
    FullDataSet<ItemDescriptor> allData = parseFullDataSet(jsonReaderFrom(json));
    assertDataSetEquals(DataBuilder.forStandardTypes()
        .addAny(FEATURES, flag1, flag2).addAny(SEGMENTS, segment1, segment2).build(), allData);
  }
  
  private void assertFlagFromJson(LDValue flagJson, Consumer<FeatureFlag> action) {
    FeatureFlag flag = (FeatureFlag)FEATURES.deserialize(flagJson.toJsonString()).getItem();
    action.accept(flag);
  }

  private void assertSegmentFromJson(LDValue segmentJson, Consumer<Segment> action) {
    Segment segment = (Segment)SEGMENTS.deserialize(segmentJson.toJsonString()).getItem();
    action.accept(segment);
  }
  
  private ObjectBuilder baseBuilder(String key) {
    return LDValue.buildObject().put("key", key).put("version", 99);
  }
  
  private LDValue flagWithAllPropertiesJson() {
    return LDValue.buildObject()
        .put("key", "flag-key")
        .put("version", 99)
        .put("on", true)
        .put("prerequisites", LDValue.buildArray()
            .build())
        .put("salt", "123")
        .put("targets", LDValue.buildArray()
            .add(LDValue.buildObject()
                .put("variation", 1)
                .put("values", LDValue.buildArray().add("key1").add("key2").build())
                .build())
            .build())
        .put("rules", LDValue.buildArray()
            .add(LDValue.buildObject()
                .put("id", "id0")
                .put("trackEvents", true)
                .put("variation", 2)
                .put("clauses", LDValue.buildArray()
                    .add(LDValue.buildObject()
                        .put("attribute", "name")
                        .put("op", "in")
                        .put("values", LDValue.buildArray().add("Lucy").add("Mina").build())
                        .put("negate", true)
                        .build())
                    .build())
                .build())
            .add(LDValue.buildObject()
                .put("id", "id1")
                .put("rollout", LDValue.buildObject()
                    .put("variations", LDValue.buildArray()
                        .add(LDValue.buildObject()
                            .put("variation", 2)
                            .put("weight", 100000)
                            .build())
                        .build())
                    .put("bucketBy", "email")
                    .put("kind", "experiment")
                    .put("seed", 123)
                    .build())
                .build())
            .build())
        .put("fallthrough", LDValue.buildObject()
            .put("variation", 1)
            .build())
        .put("offVariation", 2)
        .put("variations", LDValue.buildArray().add("a").add("b").add("c").build())
        .put("clientSide", true)
        .put("trackEvents", true)
        .put("trackEventsFallthrough", true)
        .put("debugEventsUntilDate", 1000)
        .build();
  }

  private void assertFlagHasAllProperties(FeatureFlag flag) {
    assertEquals("flag-key", flag.getKey());
    assertEquals(99, flag.getVersion());
    assertTrue(flag.isOn());
    assertEquals("123", flag.getSalt());
      
    assertNotNull(flag.getTargets());
    assertEquals(1, flag.getTargets().size());
    Target t0 = flag.getTargets().get(0);
    assertEquals(1, t0.getVariation());
    assertEquals(ImmutableSet.of("key1", "key2"), t0.getValues());
    
    assertNotNull(flag.getRules());
    assertEquals(2, flag.getRules().size());
    Rule r0 = flag.getRules().get(0);
    assertEquals("id0", r0.getId());
    assertTrue(r0.isTrackEvents());
    assertEquals(Integer.valueOf(2), r0.getVariation());
    assertNull(r0.getRollout());
  
    assertNotNull(r0.getClauses());
    Clause c0 = r0.getClauses().get(0);
    assertEquals(UserAttribute.NAME, c0.getAttribute());
    assertEquals(Operator.in, c0.getOp());
    assertEquals(ImmutableList.of(LDValue.of("Lucy"), LDValue.of("Mina")), c0.getValues());
    assertTrue(c0.isNegate());
    
    // Check for just one example of preprocessing, to verify that preprocessing has happened in
    // general for this flag - the details are covered in EvaluatorPreprocessingTest.
    assertNotNull(c0.preprocessed);
    assertEquals(ImmutableSet.of(LDValue.of("Lucy"), LDValue.of("Mina")), c0.preprocessed.valuesSet);
    
    Rule r1 = flag.getRules().get(1);
    assertEquals("id1", r1.getId());
    assertFalse(r1.isTrackEvents());
    assertNull(r1.getVariation());
    assertNotNull(r1.getRollout());
    assertNotNull(r1.getRollout().getVariations());
    assertEquals(1, r1.getRollout().getVariations().size());
    assertEquals(2, r1.getRollout().getVariations().get(0).getVariation());
    assertEquals(100000, r1.getRollout().getVariations().get(0).getWeight());
    assertEquals(UserAttribute.EMAIL, r1.getRollout().getBucketBy());
    assertEquals(RolloutKind.experiment, r1.getRollout().getKind());
    assert(r1.getRollout().isExperiment());
    assertEquals(Integer.valueOf(123), r1.getRollout().getSeed());
    
    assertNotNull(flag.getFallthrough());
    assertEquals(Integer.valueOf(1), flag.getFallthrough().getVariation());
    assertNull(flag.getFallthrough().getRollout());
    assertEquals(Integer.valueOf(2), flag.getOffVariation());
    assertEquals(ImmutableList.of(LDValue.of("a"), LDValue.of("b"), LDValue.of("c")), flag.getVariations());
    assertTrue(flag.isClientSide());
    assertTrue(flag.isTrackEvents());
    assertTrue(flag.isTrackEventsFallthrough());
    assertEquals(Long.valueOf(1000), flag.getDebugEventsUntilDate());  
  }
  
  private LDValue segmentWithAllPropertiesJson() {
    return LDValue.buildObject()
        .put("key", "segment-key")
        .put("version", 99)
        .put("included", LDValue.buildArray().add("key1").add("key2").build())
        .put("excluded", LDValue.buildArray().add("key3").add("key4").build())
        .put("salt", "123")
        .put("rules", LDValue.buildArray()
            .add(LDValue.buildObject()
                .put("weight", 50000)
                .put("bucketBy", "email")
                .put("clauses", LDValue.buildArray()
                    .add(LDValue.buildObject()
                        .put("attribute", "name")
                        .put("op", "in")
                        .put("values", LDValue.buildArray().add("Lucy").add("Mina").build())
                        .put("negate", true)
                        .build())
                    .build())
                .build())
            .add(LDValue.buildObject()
                .build())
            .build())
        .put("unbounded", true)
        .put("generation", 10)
        // Extra fields should be ignored
        .put("fallthrough", LDValue.buildObject()
            .put("variation", 1)
            .build())
        .put("variations", LDValue.buildArray().add("a").add("b").add("c").build())
        .build();
  }
  
  private void assertSegmentHasAllProperties(Segment segment) {
    assertEquals("segment-key", segment.getKey());
    assertEquals(99, segment.getVersion());
    assertEquals("123", segment.getSalt());
    assertEquals(ImmutableSet.of("key1", "key2"), segment.getIncluded());
    assertEquals(ImmutableSet.of("key3", "key4"), segment.getExcluded());
    
    assertNotNull(segment.getRules());
    assertEquals(2, segment.getRules().size());
    SegmentRule r0 = segment.getRules().get(0);
    assertEquals(Integer.valueOf(50000), r0.getWeight());
    assertNotNull(r0.getClauses());
    
    assertEquals(1, r0.getClauses().size());
    Clause c0 = r0.getClauses().get(0);
    assertEquals(UserAttribute.NAME, c0.getAttribute());
    assertEquals(Operator.in, c0.getOp());
    assertEquals(ImmutableList.of(LDValue.of("Lucy"), LDValue.of("Mina")), c0.getValues());
    assertTrue(c0.isNegate());

    assertTrue(segment.isUnbounded());
    assertEquals((Integer)10, segment.getGeneration());
    
    // Check for just one example of preprocessing, to verify that preprocessing has happened in
    // general for this segment - the details are covered in EvaluatorPreprocessingTest.
    assertNotNull(c0.preprocessed);
    assertEquals(ImmutableSet.of(LDValue.of("Lucy"), LDValue.of("Mina")), c0.preprocessed.valuesSet);
    
    SegmentRule r1 = segment.getRules().get(1);
    assertNull(r1.getWeight());
    assertNull(r1.getBucketBy());
  }
}
