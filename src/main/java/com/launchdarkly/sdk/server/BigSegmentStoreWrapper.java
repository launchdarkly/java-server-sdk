package com.launchdarkly.sdk.server;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationReason.BigSegmentsStatus;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider.Status;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider.StatusListener;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStore;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.Membership;
import com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.StoreMetadata;
import com.launchdarkly.sdk.server.interfaces.BigSegmentsConfiguration;

import org.apache.commons.codec.digest.DigestUtils;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.launchdarkly.sdk.server.subsystems.BigSegmentStoreTypes.createMembershipFromSegmentRefs;

class BigSegmentStoreWrapper implements Closeable {
  private final BigSegmentStore store;
  private final Duration staleAfter;
  private final ScheduledFuture<?> pollFuture;
  private final LoadingCache<String, Membership> cache;
  private final EventBroadcasterImpl<StatusListener, Status> statusProvider;
  private final LDLogger logger;
  private final Object statusLock = new Object();
  private Status lastStatus;

  BigSegmentStoreWrapper(BigSegmentsConfiguration config,
                         EventBroadcasterImpl<StatusListener, Status> statusProvider,
                         ScheduledExecutorService sharedExecutor,
                         LDLogger logger) {
    this.store = config.getStore();
    this.staleAfter = config.getStaleAfter();
    this.statusProvider = statusProvider;
    this.logger = logger;

    CacheLoader<String, Membership> loader = new CacheLoader<String, Membership>() {
      @Override
      public Membership load(@NonNull String key) {
        Membership membership = queryMembership(key);
        return membership == null ? createMembershipFromSegmentRefs(null, null) : membership;
      }
    };
    this.cache = CacheBuilder.newBuilder()
        .maximumSize(config.getUserCacheSize())
        .expireAfterWrite(config.getUserCacheTime())
        .build(loader);

    this.pollFuture = sharedExecutor.scheduleAtFixedRate(this::pollStoreAndUpdateStatus,
        0,
        config.getStatusPollInterval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void close() throws IOException {
    pollFuture.cancel(true);
    cache.invalidateAll();
    store.close();
  }

  /**
   * Called by the evaluator when it needs to get the Big Segment membership state for a user.
   * <p>
   * If there is a cached membership state for the user, it returns the cached state. Otherwise,
   * it converts the user key into the hash string used by the BigSegmentStore, queries the store,
   * and caches the result. The returned status value indicates whether the query succeeded, and
   * whether the result (regardless of whether it was from a new query or the cache) should be
   * considered "stale".
   *
   * @param userKey the (unhashed) user key
   * @return the query result
   */
  BigSegmentsQueryResult getUserMembership(String userKey) {
    BigSegmentsQueryResult ret = new BigSegmentsQueryResult();
    try {
      ret.membership = cache.get(userKey);
      ret.status = getStatus().isStale() ? BigSegmentsStatus.STALE : BigSegmentsStatus.HEALTHY;
    } catch (Exception e) {
      logger.error("Big Segment store returned error: {}", e.toString());
      logger.debug(e.toString(), e);
      ret.membership = null;
      ret.status = BigSegmentsStatus.STORE_ERROR;
    }
    return ret;
  }

  private Membership queryMembership(String userKey) {
    String hash = hashForUserKey(userKey);
    logger.debug("Querying Big Segment state for user hash {}", hash);
    return store.getMembership(hash);
  }

  /**
   * Returns a BigSegmentStoreStatus describing whether the store seems to be available (that is,
   * the last query to it did not return an error) and whether it is stale (that is, the last known
   * update time is too far in the past).
   * <p>
   * If we have not yet obtained that information (the poll task has not executed yet), then this
   * method immediately does a metadata query and waits for it to succeed or fail. This means that
   * if an application using Big Segments evaluates a feature flag immediately after creating the
   * SDK client, before the first status poll has happened, that evaluation may block for however
   * long it takes to query the store.
   *
   * @return the store status
   */
  Status getStatus() {
    Status ret;
    synchronized (statusLock) {
      ret = lastStatus;
    }
    if (ret != null) {
      return ret;
    }
    return pollStoreAndUpdateStatus();
  }

  Status pollStoreAndUpdateStatus() {
    boolean storeAvailable = false;
    boolean storeStale = false;
    logger.debug("Querying Big Segment store metadata");
    try {
      StoreMetadata metadata = store.getMetadata();
      storeAvailable = true;
      storeStale = metadata == null || isStale(metadata.getLastUpToDate());
    } catch (Exception e) {
      logger.error("Big Segment store status query returned error: {}", e.toString());
      logger.debug(e.toString(), e);
    }
    Status newStatus = new Status(storeAvailable, storeStale);
    Status oldStatus;
    synchronized (this.statusLock) {
      oldStatus = this.lastStatus;
      this.lastStatus = newStatus;
    }
    if (!newStatus.equals(oldStatus)) {
      logger.debug("Big Segment store status changed from {} to {}", oldStatus, newStatus);
      statusProvider.broadcast(newStatus);
    }
    return newStatus;
  }

  private boolean isStale(long updateTime) {
    return staleAfter.minusMillis(System.currentTimeMillis() - updateTime).isNegative();
  }

  static String hashForUserKey(String userKey) {
    byte[] encodedDigest = DigestUtils.sha256(userKey.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encodedDigest);
  }

  static class BigSegmentsQueryResult {
    Membership membership;
    BigSegmentsStatus status;
  }
}
