package com.launchdarkly.client;

import com.launchdarkly.client.value.LDValue;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings("javadoc")
public class UtilTest {
  @Test
  public void testDateTimeConversionWithTimeZone() {
    String validRFC3339String = "2016-04-16T17:09:12.759-07:00";
    String expected = "2016-04-17T00:09:12.759Z";

    DateTime actual = Util.jsonPrimitiveToDateTime(LDValue.of(validRFC3339String));
    Assert.assertEquals(expected, actual.toString());
  }

  @Test
  public void testDateTimeConversionWithUtc() {
    String validRFC3339String = "1970-01-01T00:00:01.001Z";

    DateTime actual = Util.jsonPrimitiveToDateTime(LDValue.of(validRFC3339String));
    Assert.assertEquals(validRFC3339String, actual.toString());
  }

  @Test
  public void testDateTimeConversionWithNoTimeZone() {
    String validRFC3339String = "2016-04-16T17:09:12.759";
    String expected = "2016-04-16T17:09:12.759Z";

    DateTime actual = Util.jsonPrimitiveToDateTime(LDValue.of(validRFC3339String));
    Assert.assertEquals(expected, actual.toString());
  }
  
  @Test
  public void testDateTimeConversionTimestampWithNoMillis() {
    String validRFC3339String = "2016-04-16T17:09:12";
    String expected = "2016-04-16T17:09:12.000Z";

    DateTime actual = Util.jsonPrimitiveToDateTime(LDValue.of(validRFC3339String));
    Assert.assertEquals(expected, actual.toString());
  }

  @Test
  public void testDateTimeConversionAsUnixMillis() {
    long unixMillis = 1000;
    String expected = "1970-01-01T00:00:01.000Z";
    DateTime actual = Util.jsonPrimitiveToDateTime(LDValue.of(unixMillis));
    Assert.assertEquals(expected, actual.withZone(DateTimeZone.UTC).toString());
  }

  @Test
  public void testDateTimeConversionCompare() {
    long aMillis = 1001;
    String bStamp = "1970-01-01T00:00:01.001Z";
    DateTime a = Util.jsonPrimitiveToDateTime(LDValue.of(aMillis));
    DateTime b = Util.jsonPrimitiveToDateTime(LDValue.of(bStamp));
    Assert.assertTrue(a.getMillis() == b.getMillis());
  }

  @Test
  public void testDateTimeConversionAsUnixMillisBeforeEpoch() {
    long unixMillis = -1000;
    DateTime actual = Util.jsonPrimitiveToDateTime(LDValue.of(unixMillis));
    Assert.assertEquals(unixMillis, actual.getMillis());
  }

  @Test
  public void testDateTimeConversionInvalidString() {
    String invalidTimestamp = "May 3, 1980";
    DateTime actual = Util.jsonPrimitiveToDateTime(LDValue.of(invalidTimestamp));
    Assert.assertNull(actual);
  }
}
