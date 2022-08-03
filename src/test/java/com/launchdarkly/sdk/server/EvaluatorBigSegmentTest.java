package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason.BigSegmentsStatus;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;

import org.junit.Test;

import java.util.Collections;

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
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.strictMock;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class EvaluatorBigSegmentTest extends EvaluatorTestBase {
  private static final LDContext testUser = LDContext.create("userkey");

  @Test
  public void bigSegmentWithNoProviderIsNotMatched() {
    DataModel.Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(1)
        .included(testUser.getKey()) // Included should be ignored for a big segment
        .build();
    DataModel.FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).withBigSegmentQueryResult(testUser.getKey(), null).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(false), result.getValue());
    assertEquals(BigSegmentsStatus.NOT_CONFIGURED, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void bigSegmentWithNoGenerationIsNotMatched() {
    // Segment without generation
    DataModel.Segment segment = segmentBuilder("segmentkey").unbounded(true).build();
    DataModel.FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(false), result.getValue());
    assertEquals(BigSegmentsStatus.NOT_CONFIGURED, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void matchedWithInclude() {
    DataModel.Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(2).build();
    DataModel.FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResult.status = BigSegmentsStatus.HEALTHY;
    queryResult.membership = createMembershipFromSegmentRefs(Collections.singleton(makeBigSegmentRef(segment)), null);
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).withBigSegmentQueryResult(testUser.getKey(), queryResult).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(true), result.getValue());
    assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void matchedWithRule() {
    DataModel.Clause clause = clauseMatchingContext(testUser);
    DataModel.SegmentRule segmentRule = segmentRuleBuilder().clauses(clause).build();
    DataModel.Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(2)
        .rules(segmentRule)
        .build();
    DataModel.FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
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
    DataModel.Clause clause = clauseMatchingContext(testUser);
    DataModel.SegmentRule segmentRule = segmentRuleBuilder().clauses(clause).build();
    DataModel.Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(2)
        .rules(segmentRule)
        .build();
    DataModel.FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResult.status = BigSegmentsStatus.HEALTHY;
    queryResult.membership = createMembershipFromSegmentRefs(null, Collections.singleton(makeBigSegmentRef(segment)));
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).withBigSegmentQueryResult(testUser.getKey(), queryResult).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(false), result.getValue());
    assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void bigSegmentStatusIsReturnedFromProvider() {
    DataModel.Segment segment = segmentBuilder("segmentkey").unbounded(true).generation(2).build();
    DataModel.FeatureFlag flag = booleanFlagWithClauses("key", clauseMatchingSegment(segment));
    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResult.status = BigSegmentsStatus.STALE;
    queryResult.membership = createMembershipFromSegmentRefs(Collections.singleton(makeBigSegmentRef(segment)), null);
    Evaluator evaluator = evaluatorBuilder().withStoredSegments(segment).withBigSegmentQueryResult(testUser.getKey(), queryResult).build();

    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(true), result.getValue());
    assertEquals(BigSegmentsStatus.STALE, result.getReason().getBigSegmentsStatus());
  }

  @Test
  public void bigSegmentStateIsQueriedOnlyOncePerUserEvenIfFlagReferencesMultipleSegments() {
    DataModel.Segment segment1 = segmentBuilder("segmentkey1").unbounded(true).generation(2).build();
    DataModel.Segment segment2 = segmentBuilder("segmentkey2").unbounded(true).generation(3).build();
    DataModel.FeatureFlag flag = flagBuilder("key")
        .on(true)
        .fallthroughVariation(0)
        .variations(false, true)
        .rules(
            ruleBuilder().variation(1).clauses(clauseMatchingSegment(segment1)).build(),
            ruleBuilder().variation(1).clauses(clauseMatchingSegment(segment2)).build()
        )
        .build();

    BigSegmentStoreWrapper.BigSegmentsQueryResult queryResult = new BigSegmentStoreWrapper.BigSegmentsQueryResult();
    queryResult.status = BigSegmentsStatus.HEALTHY;
    queryResult.membership = createMembershipFromSegmentRefs(Collections.singleton(makeBigSegmentRef(segment2)), null);

    Evaluator.Getters mockGetters = strictMock(Evaluator.Getters.class);
    expect(mockGetters.getSegment(segment1.getKey())).andReturn(segment1);
    expect(mockGetters.getBigSegments(testUser.getKey())).andReturn(queryResult);
    expect(mockGetters.getSegment(segment2.getKey())).andReturn(segment2);
    replay(mockGetters);

    Evaluator evaluator = new Evaluator(mockGetters, testLogger);
    EvalResult result = evaluator.evaluate(flag, testUser, expectNoPrerequisiteEvals());
    assertEquals(LDValue.of(true), result.getValue());
    assertEquals(BigSegmentsStatus.HEALTHY, result.getReason().getBigSegmentsStatus());
  }
}
