package com.launchdarkly.client;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static com.launchdarkly.client.TestUtil.booleanFlagWithClauses;
import static com.launchdarkly.client.TestUtil.fallthroughVariation;
import static com.launchdarkly.client.TestUtil.jbool;
import static com.launchdarkly.client.TestUtil.jint;
import static com.launchdarkly.client.TestUtil.js;
import static com.launchdarkly.client.VersionedDataKind.FEATURES;
import static com.launchdarkly.client.VersionedDataKind.SEGMENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FeatureFlagTest {

  private static LDUser BASE_USER = new LDUser.Builder("x").build();
  
  private FeatureStore featureStore;

  @Before
  public void before() {
    featureStore = new InMemoryFeatureStore();
  }

  @Test
  public void flagReturnsOffVariationIfFlagIsOff() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(false)
        .offVariation(1)
        .fallthrough(fallthroughVariation(0))
        .variations(js("fall"), js("off"), js("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore);
    
    assertEquals(js("off"), result.getValue());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }

  @Test
  public void flagReturnsNullIfFlagIsOffAndOffVariationIsUnspecified() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(false)
        .fallthrough(fallthroughVariation(0))
        .variations(js("fall"), js("off"), js("on"))
        .build();
    FeatureFlag.EvalResult result = f.evaluate(BASE_USER, featureStore);
    
    assertNull(result.getValue());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void flagReturnsOffVariationIfPrerequisiteIsNotFound() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .build();
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore);
    
    assertEquals(js("off"), result.getValue());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void flagReturnsOffVariationAndEventIfPrerequisiteIsNotMet() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .version(1)
        .build();
    FeatureFlag f1 = new FeatureFlagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(0))
        .variations(js("nogo"), js("go"))
        .version(2)
        .build();
    featureStore.upsert(FEATURES, f1);        
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore);
    
    assertEquals(js("off"), result.getValue());
    
    assertEquals(1, result.getPrerequisiteEvents().size());
    FeatureRequestEvent event = result.getPrerequisiteEvents().get(0);
    assertEquals(f1.getKey(), event.key);
    assertEquals("feature", event.kind);
    assertEquals(js("nogo"), event.value);
    assertEquals(f1.getVersion(), event.version.intValue());
    assertEquals(f0.getKey(), event.prereqOf);
  }

  @Test
  public void flagReturnsFallthroughVariationAndEventIfPrerequisiteIsMetAndThereAreNoRules() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .version(1)
        .build();
    FeatureFlag f1 = new FeatureFlagBuilder("feature1")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(js("nogo"), js("go"))
        .version(2)
        .build();
    featureStore.upsert(FEATURES, f1);        
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore);
    
    assertEquals(js("fall"), result.getValue());
    assertEquals(1, result.getPrerequisiteEvents().size());
    
    FeatureRequestEvent event = result.getPrerequisiteEvents().get(0);
    assertEquals(f1.getKey(), event.key);
    assertEquals("feature", event.kind);
    assertEquals(js("go"), event.value);
    assertEquals(f1.getVersion(), event.version.intValue());
    assertEquals(f0.getKey(), event.prereqOf);
  }

  @Test
  public void multipleLevelsOfPrerequisitesProduceMultipleEvents() throws Exception {
    FeatureFlag f0 = new FeatureFlagBuilder("feature0")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature1", 1)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .version(1)
        .build();
    FeatureFlag f1 = new FeatureFlagBuilder("feature1")
        .on(true)
        .prerequisites(Arrays.asList(new Prerequisite("feature2", 1)))
        .fallthrough(fallthroughVariation(1))
        .variations(js("nogo"), js("go"))
        .version(2)
        .build();
    FeatureFlag f2 = new FeatureFlagBuilder("feature2")
        .on(true)
        .fallthrough(fallthroughVariation(1))
        .variations(js("nogo"), js("go"))
        .version(3)
        .build();
    featureStore.upsert(FEATURES, f1);        
    featureStore.upsert(FEATURES, f2);        
    FeatureFlag.EvalResult result = f0.evaluate(BASE_USER, featureStore);
    
    assertEquals(js("fall"), result.getValue());    
    assertEquals(2, result.getPrerequisiteEvents().size());
    
    FeatureRequestEvent event0 = result.getPrerequisiteEvents().get(0);
    assertEquals(f2.getKey(), event0.key);
    assertEquals("feature", event0.kind);
    assertEquals(js("go"), event0.value);
    assertEquals(f2.getVersion(), event0.version.intValue());
    assertEquals(f1.getKey(), event0.prereqOf);

    FeatureRequestEvent event1 = result.getPrerequisiteEvents().get(1);
    assertEquals(f1.getKey(), event1.key);
    assertEquals("feature", event1.kind);
    assertEquals(js("go"), event1.value);
    assertEquals(f1.getVersion(), event1.version.intValue());
    assertEquals(f0.getKey(), event1.prereqOf);
  }
  
  @Test
  public void flagMatchesUserFromTargets() throws Exception {
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .targets(Arrays.asList(new Target(Arrays.asList("whoever", "userkey"), 2)))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .build();
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore);
    
    assertEquals(js("on"), result.getValue());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void flagMatchesUserFromRules() throws Exception {
    Clause clause = new Clause("key", Operator.in, Arrays.asList(js("userkey")), false);
    Rule rule = new Rule(Arrays.asList(clause), 2, null);
    FeatureFlag f = new FeatureFlagBuilder("feature")
        .on(true)
        .rules(Arrays.asList(rule))
        .fallthrough(fallthroughVariation(0))
        .offVariation(1)
        .variations(js("fall"), js("off"), js("on"))
        .build();
    LDUser user = new LDUser.Builder("userkey").build();
    FeatureFlag.EvalResult result = f.evaluate(user, featureStore);
    
    assertEquals(js("on"), result.getValue());
    assertEquals(0, result.getPrerequisiteEvents().size());
  }
  
  @Test
  public void clauseCanMatchBuiltInAttribute() throws Exception {
    Clause clause = new Clause("name", Operator.in, Arrays.asList(js("Bob")), false);
    FeatureFlag f = booleanFlagWithClauses(clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(jbool(true), f.evaluate(user, featureStore).getValue());
  }
  
  @Test
  public void clauseCanMatchCustomAttribute() throws Exception {
    Clause clause = new Clause("legs", Operator.in, Arrays.asList(jint(4)), false);
    FeatureFlag f = booleanFlagWithClauses(clause);
    LDUser user = new LDUser.Builder("key").custom("legs", 4).build();
    
    assertEquals(jbool(true), f.evaluate(user, featureStore).getValue());
  }
  
  @Test
  public void clauseReturnsFalseForMissingAttribute() throws Exception {
    Clause clause = new Clause("legs", Operator.in, Arrays.asList(jint(4)), false);
    FeatureFlag f = booleanFlagWithClauses(clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(jbool(false), f.evaluate(user, featureStore).getValue());
  }
  
  @Test
  public void clauseCanBeNegated() throws Exception {
    Clause clause = new Clause("name", Operator.in, Arrays.asList(js("Bob")), true);
    FeatureFlag f = booleanFlagWithClauses(clause);
    LDUser user = new LDUser.Builder("key").name("Bob").build();
    
    assertEquals(jbool(false), f.evaluate(user, featureStore).getValue());
  }
  
  @Test
  public void testSegmentMatchClauseRetrievesSegmentFromStore() throws Exception {
    Segment segment = new Segment.Builder("segkey")
        .included(Arrays.asList("foo"))
        .version(1)
        .build();
    featureStore.upsert(SEGMENTS, segment);
    
    FeatureFlag flag = segmentMatchBooleanFlag("segkey");
    LDUser user = new LDUser.Builder("foo").build();
    
    FeatureFlag.EvalResult result = flag.evaluate(user, featureStore);
    assertEquals(jbool(true), result.getValue());
  }

  @Test
  public void testSegmentMatchClauseFallsThroughIfSegmentNotFound() throws Exception {
    FeatureFlag flag = segmentMatchBooleanFlag("segkey");
    LDUser user = new LDUser.Builder("foo").build();
    
    FeatureFlag.EvalResult result = flag.evaluate(user, featureStore);
    assertEquals(jbool(false), result.getValue());
  }
 
  private FeatureFlag segmentMatchBooleanFlag(String segmentKey) {
    Clause clause = new Clause("", Operator.segmentMatch, Arrays.asList(js(segmentKey)), false);
    return booleanFlagWithClauses(clause);
  }
}
