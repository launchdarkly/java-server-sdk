package com.launchdarkly.client;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.launchdarkly.client.FeatureStoreCacheConfig.StaleValuesPolicy.EVICT;
import static com.launchdarkly.client.FeatureStoreCacheConfig.StaleValuesPolicy.REFRESH;
import static com.launchdarkly.client.FeatureStoreCacheConfig.StaleValuesPolicy.REFRESH_ASYNC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class FeatureStoreCachingTest {
  @Test
  public void disabledHasExpectedProperties() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.disabled();
    assertThat(fsc.getCacheTime(), equalTo(0L));
    assertThat(fsc.isEnabled(), equalTo(false));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void enabledHasExpectedProperties() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.enabled();
    assertThat(fsc.getCacheTime(), equalTo(FeatureStoreCacheConfig.DEFAULT_TIME_SECONDS));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.SECONDS));
    assertThat(fsc.isEnabled(), equalTo(true));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void defaultIsEnabled() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.DEFAULT;
    assertThat(fsc.getCacheTime(), equalTo(FeatureStoreCacheConfig.DEFAULT_TIME_SECONDS));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.SECONDS));
    assertThat(fsc.isEnabled(), equalTo(true));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void canSetTtl() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.enabled()
        .staleValuesPolicy(REFRESH)
        .ttl(3, TimeUnit.DAYS);
    assertThat(fsc.getCacheTime(), equalTo(3L));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.DAYS));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }
  
  @Test
  public void canSetTtlInMillis() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.enabled()
        .staleValuesPolicy(REFRESH)
        .ttlMillis(3);
    assertThat(fsc.getCacheTime(), equalTo(3L));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.MILLISECONDS));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }
  
  @Test
  public void canSetTtlInSeconds() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.enabled()
        .staleValuesPolicy(REFRESH)
        .ttlSeconds(3);
    assertThat(fsc.getCacheTime(), equalTo(3L));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.SECONDS));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }

  @Test
  public void zeroTtlMeansDisabled() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.enabled()
        .ttl(0, TimeUnit.SECONDS);
    assertThat(fsc.isEnabled(), equalTo(false));
  }
  
  @Test
  public void negativeTtlMeansDisabled() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.enabled()
        .ttl(-1, TimeUnit.SECONDS);
    assertThat(fsc.isEnabled(), equalTo(false));
  }
  
  @Test
  public void canSetStaleValuesPolicy() {
    FeatureStoreCacheConfig fsc = FeatureStoreCacheConfig.enabled()
        .ttlMillis(3)
        .staleValuesPolicy(REFRESH_ASYNC);
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH_ASYNC));
    assertThat(fsc.getCacheTime(), equalTo(3L));
    assertThat(fsc.getCacheTimeUnit(), equalTo(TimeUnit.MILLISECONDS));
  }
  
  @Test
  public void equalityUsesTime() {
    FeatureStoreCacheConfig fsc1 = FeatureStoreCacheConfig.enabled().ttlMillis(3);
    FeatureStoreCacheConfig fsc2 = FeatureStoreCacheConfig.enabled().ttlMillis(3);
    FeatureStoreCacheConfig fsc3 = FeatureStoreCacheConfig.enabled().ttlMillis(4);
    assertThat(fsc1.equals(fsc2), equalTo(true));
    assertThat(fsc1.equals(fsc3), equalTo(false));
  }

  @Test
  public void equalityUsesTimeUnit() {
    FeatureStoreCacheConfig fsc1 = FeatureStoreCacheConfig.enabled().ttlMillis(3);
    FeatureStoreCacheConfig fsc2 = FeatureStoreCacheConfig.enabled().ttlMillis(3);
    FeatureStoreCacheConfig fsc3 = FeatureStoreCacheConfig.enabled().ttlSeconds(3);
    assertThat(fsc1.equals(fsc2), equalTo(true));
    assertThat(fsc1.equals(fsc3), equalTo(false));
  }

  @Test
  public void equalityUsesStaleValuesPolicy() {
    FeatureStoreCacheConfig fsc1 = FeatureStoreCacheConfig.enabled().staleValuesPolicy(EVICT);
    FeatureStoreCacheConfig fsc2 = FeatureStoreCacheConfig.enabled().staleValuesPolicy(EVICT);
    FeatureStoreCacheConfig fsc3 = FeatureStoreCacheConfig.enabled().staleValuesPolicy(REFRESH);
    assertThat(fsc1.equals(fsc2), equalTo(true));
    assertThat(fsc1.equals(fsc3), equalTo(false));
  }
}
