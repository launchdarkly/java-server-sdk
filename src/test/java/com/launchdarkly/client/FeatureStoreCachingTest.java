package com.launchdarkly.client;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.launchdarkly.client.FeatureStoreCaching.StaleValuesPolicy.EVICT;
import static com.launchdarkly.client.FeatureStoreCaching.StaleValuesPolicy.REFRESH;
import static com.launchdarkly.client.FeatureStoreCaching.StaleValuesPolicy.REFRESH_ASYNC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class FeatureStoreCachingTest {
  @Test
  public void disabledHasExpectedProperties() {
    FeatureStoreCaching fsc = FeatureStoreCaching.disabled();
    assertThat(fsc.getCacheTime(), equalTo(0L));
    assertThat(fsc.isEnabled(), equalTo(false));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void enabledHasExpectedProperties() {
    FeatureStoreCaching fsc = FeatureStoreCaching.enabled();
    assertThat(fsc.getCacheTime(), equalTo(FeatureStoreCaching.DEFAULT_TIME_SECONDS));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.SECONDS));
    assertThat(fsc.isEnabled(), equalTo(true));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void defaultIsEnabled() {
    FeatureStoreCaching fsc = FeatureStoreCaching.DEFAULT;
    assertThat(fsc.getCacheTime(), equalTo(FeatureStoreCaching.DEFAULT_TIME_SECONDS));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.SECONDS));
    assertThat(fsc.isEnabled(), equalTo(true));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void canSetTtl() {
    FeatureStoreCaching fsc = FeatureStoreCaching.enabled()
        .staleValuesPolicy(REFRESH)
        .ttl(3, TimeUnit.DAYS);
    assertThat(fsc.getCacheTime(), equalTo(3L));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.DAYS));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }
  
  @Test
  public void canSetTtlInMillis() {
    FeatureStoreCaching fsc = FeatureStoreCaching.enabled()
        .staleValuesPolicy(REFRESH)
        .ttlMillis(3);
    assertThat(fsc.getCacheTime(), equalTo(3L));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.MILLISECONDS));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }
  
  @Test
  public void canSetTtlInSeconds() {
    FeatureStoreCaching fsc = FeatureStoreCaching.enabled()
        .staleValuesPolicy(REFRESH)
        .ttlSeconds(3);
    assertThat(fsc.getCacheTime(), equalTo(3L));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.SECONDS));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }

  @Test
  public void zeroTtlMeansDisabled() {
    FeatureStoreCaching fsc = FeatureStoreCaching.enabled()
        .ttl(0, TimeUnit.SECONDS);
    assertThat(fsc.isEnabled(), equalTo(false));
  }
  
  @Test
  public void negativeTtlMeansDisabled() {
    FeatureStoreCaching fsc = FeatureStoreCaching.enabled()
        .ttl(-1, TimeUnit.SECONDS);
    assertThat(fsc.isEnabled(), equalTo(false));
  }
  
  @Test
  public void canSetStaleValuesPolicy() {
    FeatureStoreCaching fsc = FeatureStoreCaching.enabled()
        .ttlMillis(3)
        .staleValuesPolicy(REFRESH_ASYNC);
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH_ASYNC));
    assertThat(fsc.getCacheTime(), equalTo(3L));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.MILLISECONDS));
  }
  
  @Test
  public void equalityUsesTime() {
    FeatureStoreCaching fsc1 = FeatureStoreCaching.enabled().ttlMillis(3);
    FeatureStoreCaching fsc2 = FeatureStoreCaching.enabled().ttlMillis(3);
    FeatureStoreCaching fsc3 = FeatureStoreCaching.enabled().ttlMillis(4);
    assertThat(fsc1.equals(fsc2), equalTo(true));
    assertThat(fsc1.equals(fsc3), equalTo(false));
  }

  @Test
  public void equalityUsesTimeUnit() {
    FeatureStoreCaching fsc1 = FeatureStoreCaching.enabled().ttlMillis(3);
    FeatureStoreCaching fsc2 = FeatureStoreCaching.enabled().ttlMillis(3);
    FeatureStoreCaching fsc3 = FeatureStoreCaching.enabled().ttlSeconds(3);
    assertThat(fsc1.equals(fsc2), equalTo(true));
    assertThat(fsc1.equals(fsc3), equalTo(false));
  }

  @Test
  public void equalityUsesStaleValuesPolicy() {
    FeatureStoreCaching fsc1 = FeatureStoreCaching.enabled().staleValuesPolicy(EVICT);
    FeatureStoreCaching fsc2 = FeatureStoreCaching.enabled().staleValuesPolicy(EVICT);
    FeatureStoreCaching fsc3 = FeatureStoreCaching.enabled().staleValuesPolicy(REFRESH);
    assertThat(fsc1.equals(fsc2), equalTo(true));
    assertThat(fsc1.equals(fsc3), equalTo(false));
  }
}
