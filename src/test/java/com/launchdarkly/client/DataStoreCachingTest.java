package com.launchdarkly.client;

import org.junit.Test;

import java.time.Duration;

import static com.launchdarkly.client.DataStoreCacheConfig.StaleValuesPolicy.EVICT;
import static com.launchdarkly.client.DataStoreCacheConfig.StaleValuesPolicy.REFRESH;
import static com.launchdarkly.client.DataStoreCacheConfig.StaleValuesPolicy.REFRESH_ASYNC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class DataStoreCachingTest {
  @Test
  public void disabledHasExpectedProperties() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.disabled();
    assertThat(fsc.getCacheTime(), equalTo(Duration.ZERO));
    assertThat(fsc.isEnabled(), equalTo(false));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void enabledHasExpectedProperties() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.enabled();
    assertThat(fsc.getCacheTime(), equalTo(DataStoreCacheConfig.DEFAULT_TIME));
    assertThat(fsc.isEnabled(), equalTo(true));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void defaultIsEnabled() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.DEFAULT;
    assertThat(fsc.getCacheTime(), equalTo(DataStoreCacheConfig.DEFAULT_TIME));
    assertThat(fsc.isEnabled(), equalTo(true));
    assertThat(fsc.isInfiniteTtl(), equalTo(false));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(EVICT));
  }
  
  @Test
  public void canSetTtl() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.enabled()
        .staleValuesPolicy(REFRESH)
        .ttl(Duration.ofDays(3));
    assertThat(fsc.getCacheTime(), equalTo(Duration.ofDays(3)));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }
  
  @Test
  public void canSetTtlInMillis() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.enabled()
        .staleValuesPolicy(REFRESH)
        .ttlMillis(3);
    assertThat(fsc.getCacheTime(), equalTo(Duration.ofMillis(3)));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }
  
  @Test
  public void canSetTtlInSeconds() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.enabled()
        .staleValuesPolicy(REFRESH)
        .ttlSeconds(3);
    assertThat(fsc.getCacheTime(), equalTo(Duration.ofSeconds(3)));
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH));
  }

  @Test
  public void zeroTtlMeansDisabled() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.enabled()
        .ttl(Duration.ZERO);
    assertThat(fsc.isEnabled(), equalTo(false));
    assertThat(fsc.isInfiniteTtl(), equalTo(false));
  }
  
  @Test
  public void negativeTtlMeansInfinite() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.enabled()
        .ttl(Duration.ofSeconds(-1));
    assertThat(fsc.isEnabled(), equalTo(true));
    assertThat(fsc.isInfiniteTtl(), equalTo(true));
  }
  
  @Test
  public void canSetStaleValuesPolicy() {
    DataStoreCacheConfig fsc = DataStoreCacheConfig.enabled()
        .ttlMillis(3)
        .staleValuesPolicy(REFRESH_ASYNC);
    assertThat(fsc.getStaleValuesPolicy(), equalTo(REFRESH_ASYNC));
    assertThat(fsc.getCacheTime(), equalTo(Duration.ofMillis(3)));
  }
  
  @Test
  public void equalityUsesTime() {
    DataStoreCacheConfig fsc1 = DataStoreCacheConfig.enabled().ttlMillis(3);
    DataStoreCacheConfig fsc2 = DataStoreCacheConfig.enabled().ttlMillis(3);
    DataStoreCacheConfig fsc3 = DataStoreCacheConfig.enabled().ttlMillis(4);
    assertThat(fsc1.equals(fsc2), equalTo(true));
    assertThat(fsc1.equals(fsc3), equalTo(false));
  }

  @Test
  public void equalityUsesStaleValuesPolicy() {
    DataStoreCacheConfig fsc1 = DataStoreCacheConfig.enabled().staleValuesPolicy(EVICT);
    DataStoreCacheConfig fsc2 = DataStoreCacheConfig.enabled().staleValuesPolicy(EVICT);
    DataStoreCacheConfig fsc3 = DataStoreCacheConfig.enabled().staleValuesPolicy(REFRESH);
    assertThat(fsc1.equals(fsc2), equalTo(true));
    assertThat(fsc1.equals(fsc3), equalTo(false));
  }
}
