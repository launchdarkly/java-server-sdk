package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;

import static com.launchdarkly.client.Util.configureHttpClientBuilder;
import static com.launchdarkly.client.Util.shutdownHttpClient;
import static org.junit.Assert.assertEquals;

import okhttp3.OkHttpClient;

public class UtilTest {
  @Test
  public void testDateTimeConversionWithTimeZone() {
    String validRFC3339String = "2016-04-16T17:09:12.759-07:00";
    String expected = "2016-04-17T00:09:12.759Z";

    DateTime actual = Util.jsonPrimitiveToDateTime(new JsonPrimitive(validRFC3339String));
    Assert.assertEquals(expected, actual.toString());
  }

  @Test
  public void testDateTimeConversionWithUtc() {
    String validRFC3339String = "1970-01-01T00:00:01.001Z";

    DateTime actual = Util.jsonPrimitiveToDateTime(new JsonPrimitive(validRFC3339String));
    Assert.assertEquals(validRFC3339String, actual.toString());
  }

  @Test
  public void testDateTimeConversionWithNoTimeZone() {
    String validRFC3339String = "2016-04-16T17:09:12.759";
    String expected = "2016-04-16T17:09:12.759Z";

    DateTime actual = Util.jsonPrimitiveToDateTime(new JsonPrimitive(validRFC3339String));
    Assert.assertEquals(expected, actual.toString());
  }
  
  @Test
  public void testDateTimeConversionTimestampWithNoMillis() {
    String validRFC3339String = "2016-04-16T17:09:12";
    String expected = "2016-04-16T17:09:12.000Z";

    DateTime actual = Util.jsonPrimitiveToDateTime(new JsonPrimitive(validRFC3339String));
    Assert.assertEquals(expected, actual.toString());
  }

  @Test
  public void testDateTimeConversionAsUnixMillis() {
    long unixMillis = 1000;
    String expected = "1970-01-01T00:00:01.000Z";
    DateTime actual = Util.jsonPrimitiveToDateTime(new JsonPrimitive(unixMillis));
    Assert.assertEquals(expected, actual.withZone(DateTimeZone.UTC).toString());
  }

  @Test
  public void testDateTimeConversionCompare() {
    long aMillis = 1001;
    String bStamp = "1970-01-01T00:00:01.001Z";
    DateTime a = Util.jsonPrimitiveToDateTime(new JsonPrimitive(aMillis));
    DateTime b = Util.jsonPrimitiveToDateTime(new JsonPrimitive(bStamp));
    Assert.assertTrue(a.getMillis() == b.getMillis());
  }

  @Test
  public void testDateTimeConversionAsUnixMillisBeforeEpoch() {
    long unixMillis = -1000;
    DateTime actual = Util.jsonPrimitiveToDateTime(new JsonPrimitive(unixMillis));
    Assert.assertEquals(unixMillis, actual.getMillis());
  }

  @Test
  public void testDateTimeConversionInvalidString() {
    String invalidTimestamp = "May 3, 1980";
    DateTime actual = Util.jsonPrimitiveToDateTime(new JsonPrimitive(invalidTimestamp));
    Assert.assertNull(actual);
  }
  
  @Test
  public void testConnectTimeoutSpecifiedInSeconds() {
    LDConfig config = new LDConfig.Builder().connectTimeout(3).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.connectTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }

  @Test
  public void testConnectTimeoutSpecifiedInMilliseconds() {
    LDConfig config = new LDConfig.Builder().connectTimeoutMillis(3000).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.connectTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }
  
  @Test
  public void testSocketTimeoutSpecifiedInSeconds() {
    LDConfig config = new LDConfig.Builder().socketTimeout(3).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.readTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }

  @Test
  public void testSocketTimeoutSpecifiedInMilliseconds() {
    LDConfig config = new LDConfig.Builder().socketTimeoutMillis(3000).build();
    OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
    configureHttpClientBuilder(config, httpBuilder);
    OkHttpClient httpClient = httpBuilder.build();
    try {
      assertEquals(3000, httpClient.readTimeoutMillis());
    } finally {
      shutdownHttpClient(httpClient);
    }
  }
}
