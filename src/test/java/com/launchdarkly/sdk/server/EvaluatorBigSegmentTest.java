package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationReason.BigSegmentsStatus;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.DataModel.Clause;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataModel.SegmentRule;

import org.junit.Test;

import static com.launchdarkly.sdk.server.Evaluator.makeBigSegmentRef;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static com.launchdarkly.sdk.server.ModelBuilders.booleanFlagWithClauses;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingContext;
import static com.launchdarkly.sdk.server.ModelBuilders.clauseMatchingSegment;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.ruleBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentRuleBuilder;
import static com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.createMembershipFromSegmentRefs;
import static java.util.Arrays.asList;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.strictMock;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EvaluatorBigSegmentTest extends EvaluatorTestBase {
  private static final LDContext testUser = LDContext.create("userkey");

  @Test
  public void bigSegmentWithNoProviderIsNotMatched() {
    Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(1)
        .included(testUser.getKey()) // Included should be ignored for a big segment
        .build();
    FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).withBigSegmentQueryResult(testUser.getKey(), null).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(false), result.getValue());
    assertEquals(BigSegmentsStatus.NOT_CONFIGURED, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void bigSegmentWithNoGenerationIsNotMatched() {
    // Segment without generation
    Segment segment = segmentBuilder("segmentkey").unbounded(true).build();
    FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(false), result.getValue());
    assertEquals(BigSegmentsStatus.NOT_CONFIGURED, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void matchedWithIncludeForDefaultKind() {
    testMatchedWithInclude(false, false);
    testMatchedWithInclude(false, true);
  }

  @Test
  public void matchedWithIncludeForNonDefaultKind() {
    testMatchedWithInclude(true, false);
    testMatchedWithInclude(true, true);
  }

  private void testMatchedWithInclude(boolean nonDefaultKind, boolean multiKindContext) {
    String targetKey = "contextkey";
    ContextKind kind1 = ContextKind.of("kind1");
    LDContext singleKindContext = nonDefaultKind ? LDContext.create(kind1, targetKey) : LDContext.create(targetKey);
    LDContext evalContext = multiKindContext ?
        LDContext.createMulti(singleKindContext, LDContext.create(ContextKind.of("kind2"), "key2")) :
        singleKindContext;
        
    Segment segment = segmentBuilder("segmentkey")
        .unbounded(true)
        .unboundedContextKind(nonDefaultKind ? kind1 : null)
        .generation(2)
        .build();
    FeatureFlag flag = booleanFlagWithClauses("flagkey", clauseMatchingSegment(segment));
    
    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResult.status = BigSegmentsStatus.HEALTHY;
    queryResult.membership = createMembershipFromSegmentRefs(asList(makeBigSegmentRef(segment)), null);
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment)
        .withBigSegmentQueryResult(targetKey, queryResult).build();

    EvalResult result = evaluator.evaluate(flag, evalContext, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(true), result.getValue());
    assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
  }
  
  @Test
  public void matchedWithRule() {
    Clause clause = clauseMatchingContext(testUser);
    SegmentRule segmentRule = segmentRuleBuilder().clauses(clause).build();
    Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(2)
        .rules(segmentRule)
        .build();
    FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResult.status = BigSegmentsStatus.HEALTHY;
    queryResult.membership = createMembershipFromSegmentRefs(null, null);
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).withBigSegmentQueryResult(testUser.getKey(), queryResult).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(true), result.getValue());
    assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void unmatchedByExcludeRegardlessOfRule() {
    Clause clause = clauseMatchingContext(testUser);
    SegmentRule segmentRule = segmentRuleBuilder().clauses(clause).build();
    Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(2)
        .rules(segmentRule)
        .build();
    FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResult.status = BigSegmentsStatus.HEALTHY;
    queryResult.membership = createMembershipFromSegmentRefs(null, asList(makeBigSegmentRef(segment)));
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).withBigSegmentQueryResult(testUser.getKey(), queryResult).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(false), result.getValue());
    assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void bigSegmentStatusIsReturnedFromProvider() {
    Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(2).build();
    FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResult.status = BigSegmentsStatus.STALE;
    queryResult.membership = createMembershipFromSegmentRefs(asList(makeBigSegmentRef(segment)), null);
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).withBigSegmentQueryResult(testUser.getKey(), queryResult).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(true), result.getValue());
    assertEquals(BigSegmentsStatus.STALE, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void bigSegmentStateIsQueriedOnlyOncePerKeyEvenIfFlagReferencesMultipleSegments() {
    ContextKind kind1 = ContextKind.of("kind1"), kind2 = ContextKind.of("kind2"), kind3 = ContextKind.of("kind3");
    String key1 = "contextkey1", key2 = "contextkey2";
    LDContext context = LDContext.createMulti(
        LDContext.create(kind1, key1),
        LDContext.create(kind2, key2),
        LDContext.create(kind3, key2) // deliberately using same key for kind2 and kind3
        );

    Segment segment1 = segmentBuilder("segmentkey1").unbounded(true).unboundedContextKind(kind1).generation(2).build();
    Segment segment2 = segmentBuilder("segmentkey2").unbounded(true).unboundedContextKind(kind2).generation(3).build();
    Segment segment3 = segmentBuilder("segmentkey3").unbounded(true).unboundedContextKind(kind3).generation(4).build();
    
    // Set up the flag with a rule for each segment
    FeatureFlag flag = flagBuilder("key")
        .on(true)
        .fallthroughVariation(0)
        .variations(false, true)
        .rules(
            ruleBuilder().variation(1).clauses(clauseMatchingSegment(segment1)).build(),
            ruleBuilder().variation(1).clauses(clauseMatchingSegment(segment2)).build(),
            ruleBuilder().variation(1).clauses(clauseMatchingSegment(segment3)).build()
        )
        .build();

    // Set up the fake big segment store so that it will report a match only for segment3 with key2.
    // Since segment1 and segment2 won't match, all three rules will be evaluated, and since each
    // segment uses a different ContextKind, we will be testing keys from all three of the individual
    // contexts. But two of those are the same key, and since big segment queries are cached per key,
    // we should only see a single query for that one.
    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResultForKey2 = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResultForKey2.status = BigSegmentsStatus.HEALTHY;
    queryResultForKey2.membership = createMembershipFromSegmentRefs(asList(makeBigSegmentRef(segment3)), null);

    Evaluator.Getters mockGetters = strictMock(Evaluator.Getters.class);
    expect(mockGetters.getSegment(segment1.getKey())).andReturn(segment1);
    expect(mockGetters.getBigSegments(key1)).andReturn(null).times(1);
    expect(mockGetters.getSegment(segment2.getKey())).andReturn(segment2);
    expect(mockGetters.getBigSegments(key2)).andReturn(queryResultForKey2).times(1);
    expect(mockGetters.getSegment(segment3.getKey())).andReturn(segment3);
    replay(mockGetters);

    Evaluator evaluator = new Evaluator(mockGetters, testLogger);
    EvalResult result = evaluator.evaluate(flag, context, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(true), result.getValue());
    assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
  }
}
