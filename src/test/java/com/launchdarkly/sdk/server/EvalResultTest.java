package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;

import org.junit.Test;

import java.util.function.Function;

import static com.launchdarkly.sdk.EvaluationDetail.NO_VARIATION;
import static com.launchdarkly.sdk.EvaluationReason.ErrorKind.WRONG_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@SuppressWarnings("javadoc")
public class EvalResultTest {
  private static final LDValue SOME_VALUE = LDValue.of("value");
  private static final LDValue ARRAY_VALUE = LDValue.arrayOf();
  private static final LDValue OBJECT_VALUE = LDValue.buildObject().build();
  private static final int SOME_VARIATION = 11;
  private static final EvaluationReason SOME_REASON = EvaluationReason.fallthrough();

  @Test
  public void getValue() {
    assertThat(EvalResult.of(EvaluationDetail.fromValue(SOME_VALUE, SOME_VARIATION, SOME_REASON)).getValue(),
        equalTo(SOME_VALUE));
    assertThat(EvalResult.of(SOME_VALUE, SOME_VARIATION, SOME_REASON).getValue(),
        equalTo(SOME_VALUE));
  }

  @Test
  public void getVariationIndex() {
    assertThat(EvalResult.of(EvaluationDetail.fromValue(SOME_VALUE, SOME_VARIATION, SOME_REASON)).getVariationIndex(),
        equalTo(SOME_VARIATION));
    assertThat(EvalResult.of(SOME_VALUE, SOME_VARIATION, SOME_REASON).getVariationIndex(),
        equalTo(SOME_VARIATION));
  }

  @Test
  public void getReason() {
    assertThat(EvalResult.of(EvaluationDetail.fromValue(SOME_VALUE, SOME_VARIATION, SOME_REASON)).getReason(),
        equalTo(SOME_REASON));
    assertThat(EvalResult.of(SOME_VALUE, SOME_VARIATION, SOME_REASON).getReason(),
        equalTo(SOME_REASON));
  }

  @Test
  public void isNoVariation() {
    assertThat(EvalResult.of(EvaluationDetail.fromValue(SOME_VALUE, SOME_VARIATION, SOME_REASON)).isNoVariation(),
        is(false));
    assertThat(EvalResult.of(SOME_VALUE, SOME_VARIATION, SOME_REASON).isNoVariation(),
        is(false));

    assertThat(EvalResult.of(EvaluationDetail.fromValue(SOME_VALUE, NO_VARIATION, SOME_REASON)).isNoVariation(),
        is(true));
    assertThat(EvalResult.of(SOME_VALUE, NO_VARIATION, SOME_REASON).isNoVariation(),
        is(true));
  }
  
  @Test
  public void getAnyType() {
    testForType(SOME_VALUE, SOME_VALUE, r -> r.getAnyType());
  }
  
  @Test
  public void getAsBoolean() {
    testForType(true, LDValue.of(true), r -> r.getAsBoolean());
    
    testWrongType(false, LDValue.ofNull(), r -> r.getAsBoolean());
    testWrongType(false, LDValue.of(1), r -> r.getAsBoolean());
    testWrongType(false, LDValue.of("a"), r -> r.getAsBoolean());
    testWrongType(false, ARRAY_VALUE, r -> r.getAsBoolean());
    testWrongType(false, OBJECT_VALUE, r -> r.getAsBoolean());
  }
  
  @Test
  public void getAsInteger() {
    testForType(99, LDValue.of(99), r -> r.getAsInteger());
    testForType(99, LDValue.of(99.25), r -> r.getAsInteger());
    
    testWrongType(0, LDValue.ofNull(), r -> r.getAsInteger());
    testWrongType(0, LDValue.of(true), r -> r.getAsInteger());
    testWrongType(0, LDValue.of("a"), r -> r.getAsInteger());
    testWrongType(0, ARRAY_VALUE, r -> r.getAsInteger());
    testWrongType(0, OBJECT_VALUE, r -> r.getAsInteger());
  }
  
  @Test
  public void getAsDouble() {
    testForType((double)99, LDValue.of(99), r -> r.getAsDouble());
    testForType((double)99.25, LDValue.of(99.25), r -> r.getAsDouble());
    
    testWrongType((double)0, LDValue.ofNull(), r -> r.getAsDouble());
    testWrongType((double)0, LDValue.of(true), r -> r.getAsDouble());
    testWrongType((double)0, LDValue.of("a"), r -> r.getAsDouble());
    testWrongType((double)0, ARRAY_VALUE, r -> r.getAsDouble());
    testWrongType((double)0, OBJECT_VALUE, r -> r.getAsDouble());
  }

  @Test
  public void getAsString() {
    testForType("a", LDValue.of("a"), r -> r.getAsString());
    testForType((String)null, LDValue.ofNull(), r -> r.getAsString());

    testWrongType((String)null, LDValue.of(true), r -> r.getAsString());
    testWrongType((String)null, LDValue.of(1), r -> r.getAsString());
    testWrongType((String)null, ARRAY_VALUE, r -> r.getAsString());
    testWrongType((String)null, OBJECT_VALUE, r -> r.getAsString());
  }
  
  @Test
  public void withReason() {
    EvalResult r = EvalResult.of(LDValue.of(true), SOME_VARIATION, EvaluationReason.fallthrough());
    
    EvalResult r1 = r.withReason(EvaluationReason.off());
    assertThat(r1.getReason(), equalTo(EvaluationReason.off()));
    assertThat(r1.getValue(), equalTo(r.getValue()));
    assertThat(r1.getVariationIndex(), equalTo(r.getVariationIndex()));
  }
  
  @Test
  public void withForceReasonTracking() {
    EvalResult r = EvalResult.of(SOME_VALUE, SOME_VARIATION, SOME_REASON);
    assertThat(r.isForceReasonTracking(), is(false));
    
    EvalResult r0 = r.withForceReasonTracking(false);
    assertThat(r0, sameInstance(r));
    
    EvalResult r1 = r.withForceReasonTracking(true);
    assertThat(r1.isForceReasonTracking(), is(true));
    assertThat(r1.getAnyType(), sameInstance(r.getAnyType()));
  }
  
  private <T> void testForType(T value, LDValue ldValue, Function<EvalResult, T> getter) {
    assertThat(
        getter.apply(EvalResult.of(EvaluationDetail.fromValue(ldValue, SOME_VARIATION, SOME_REASON))),
        equalTo(EvaluationDetail.fromValue(value, SOME_VARIATION, SOME_REASON))
        );
    assertThat(
        getter.apply(EvalResult.of(EvaluationDetail.fromValue(ldValue, SOME_VARIATION, SOME_REASON))),
        equalTo(EvaluationDetail.fromValue(value, SOME_VARIATION, SOME_REASON))
        );
  }
  
  private <T> void testWrongType(T value, LDValue ldValue, Function<EvalResult, T> getter) {
    assertThat(
        getter.apply(EvalResult.of(EvaluationDetail.fromValue(ldValue, SOME_VARIATION, SOME_REASON))),
        equalTo(EvaluationDetail.fromValue(value, EvaluationDetail.NO_VARIATION, EvaluationReason.error(WRONG_TYPE)))
        );
    assertThat(
        getter.apply(EvalResult.of(EvaluationDetail.fromValue(ldValue, SOME_VARIATION, SOME_REASON))),
        equalTo(EvaluationDetail.fromValue(value, EvaluationDetail.NO_VARIATION, EvaluationReason.error(WRONG_TYPE)))
        );
  }
}
