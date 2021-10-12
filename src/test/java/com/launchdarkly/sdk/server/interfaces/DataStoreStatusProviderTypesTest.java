package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.CacheStats;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider.Status;
import com.launchdarkly.testhelpers.TypeBehavior;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class DataStoreStatusProviderTypesTest {
  @Test
  public void statusProperties() {
    Status s1 = new Status(true, false);
    assertThat(s1.isAvailable(), equalTo(true));
    assertThat(s1.isRefreshNeeded(), equalTo(false));

    Status s2 = new Status(false, true);
    assertThat(s2.isAvailable(), equalTo(false));
    assertThat(s2.isRefreshNeeded(), equalTo(true));
  }
  
  @Test
  public void statusEquality() {
    List<TypeBehavior.ValueFactory<Status>> allPermutations = new ArrayList<>();
    allPermutations.add(() -> new Status(false, false));
    allPermutations.add(() -> new Status(false, true));
    allPermutations.add(() -> new Status(true, false));
    allPermutations.add(() -> new Status(true, true));
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @Test
  public void statusStringRepresentation() {
    assertThat(new Status(true, false).toString(), equalTo("Status(true,false)"));
  }
  
  @Test
  public void cacheStatsProperties() {
    CacheStats stats = new CacheStats(1, 2, 3, 4, 5, 6);
    assertThat(stats.getHitCount(), equalTo(1L));
    assertThat(stats.getMissCount(), equalTo(2L));
    assertThat(stats.getLoadSuccessCount(), equalTo(3L));
    assertThat(stats.getLoadExceptionCount(), equalTo(4L));
    assertThat(stats.getTotalLoadTime(), equalTo(5L));
    assertThat(stats.getEvictionCount(), equalTo(6L));
  }
  
  @Test
  public void cacheStatsEquality() {
    List<TypeBehavior.ValueFactory<CacheStats>> allPermutations = new ArrayList<>();
    int[] values = new int[] { 0, 1, 2 };
    for (int hit: values) {
      for (int miss: values) {
        for (int loadSuccess: values) {
          for (int loadException: values) {
            for (int totalLoad: values) {
              for (int eviction: values) {
                allPermutations.add(() -> new CacheStats(hit, miss, loadSuccess, loadException, totalLoad, eviction));
              }
            }
          }
        }
      }
    }
    TypeBehavior.checkEqualsAndHashCode(allPermutations);
  }
  
  @Test
  public void cacheStatsStringRepresentation() {
    CacheStats stats = new CacheStats(1, 2, 3, 4, 5, 6);
    assertThat(stats.toString(), equalTo("{hit=1, miss=2, loadSuccess=3, loadException=4, totalLoadTime=5, evictionCount=6}"));
  }
}
