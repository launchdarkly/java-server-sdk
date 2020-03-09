package com.launchdarkly.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.launchdarkly.client.DataModel.Clause;
import com.launchdarkly.client.DataModel.FeatureFlag;
import com.launchdarkly.client.DataModel.Operator;
import com.launchdarkly.client.DataModel.Rule;
import com.launchdarkly.client.DataModel.Segment;
import com.launchdarkly.client.DataModel.SegmentRule;
import com.launchdarkly.client.DataModel.Target;
import com.launchdarkly.client.interfaces.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.client.value.LDValue;

import org.junit.Test;

import static com.launchdarkly.client.DataModel.DataKinds.FEATURES;
import static com.launchdarkly.client.DataModel.DataKinds.SEGMENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class DataModelSerializationTest {
  @Test
  public void flagIsDeserializedWithAllProperties() {
    String json0 = flagWithAllPropertiesJson().toJsonString();
    FeatureFlag flag0 = (FeatureFlag)FEATURES.deserialize(json0).getItem();
    assertFlagHasAllProperties(flag0);
    
    String json1 = FEATURES.serialize(new ItemDescriptor(flag0.getVersion(), flag0));
    FeatureFlag flag1 = (FeatureFlag)FEATURES.deserialize(json1).getItem();
    assertFlagHasAllProperties(flag1);
  }
  
  @Test
  public void flagIsDeserializedWithMinimalProperties() {
    String json = LDValue.buildObject().put("key", "flag-key").put("version", 99).build().toJsonString();
    FeatureFlag flag = (FeatureFlag)FEATURES.deserialize(json).getItem();
    assertEquals("flag-key", flag.getKey());
    assertEquals(99, flag.getVersion());
    assertFalse(flag.isOn());
    assertNull(flag.getSalt());    
    assertNull(flag.getTargets());
    assertNull(flag.getRules());    
    assertNull(flag.getFallthrough());
    assertNull(flag.getOffVariation());
    assertNull(flag.getVariations());
    assertFalse(flag.isClientSide());
    assertFalse(flag.isTrackEvents());
    assertFalse(flag.isTrackEventsFallthrough());
    assertNull(flag.getDebugEventsUntilDate());
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
    assertNull(segment.getIncluded());
    assertNull(segment.getExcluded());
    assertNull(segment.getRules());
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
                        .put("values", LDValue.buildArray().add("Lucy").build())
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
    assertEquals(new Integer(2), r0.getVariation());
    assertNull(r0.getRollout());
  
    assertNotNull(r0.getClauses());
    Clause c0 = r0.getClauses().get(0);
    assertEquals(UserAttribute.NAME, c0.getAttribute());
    assertEquals(Operator.in, c0.getOp());
    assertEquals(ImmutableList.of(LDValue.of("Lucy")), c0.getValues());
    assertTrue(c0.isNegate());
    
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
    
    assertNotNull(flag.getFallthrough());
    assertEquals(new Integer(1), flag.getFallthrough().getVariation());
    assertNull(flag.getFallthrough().getRollout());
    assertEquals(new Integer(2), flag.getOffVariation());
    assertEquals(ImmutableList.of(LDValue.of("a"), LDValue.of("b"), LDValue.of("c")), flag.getVariations());
    assertTrue(flag.isClientSide());
    assertTrue(flag.isTrackEvents());
    assertTrue(flag.isTrackEventsFallthrough());
    assertEquals(new Long(1000), flag.getDebugEventsUntilDate());  
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
                        .put("values", LDValue.buildArray().add("Lucy").build())
                        .put("negate", true)
                        .build())
                    .build())
                .build())
            .add(LDValue.buildObject()
                .build())
            .build())
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
    assertEquals(new Integer(50000), r0.getWeight());
    assertNotNull(r0.getClauses());
    assertEquals(1, r0.getClauses().size());
    Clause c0 = r0.getClauses().get(0);
    assertEquals(UserAttribute.NAME, c0.getAttribute());
    assertEquals(Operator.in, c0.getOp());
    assertEquals(ImmutableList.of(LDValue.of("Lucy")), c0.getValues());
    assertTrue(c0.isNegate());
    SegmentRule r1 = segment.getRules().get(1);
    assertNull(r1.getWeight());
    assertNull(r1.getBucketBy());
  }
}