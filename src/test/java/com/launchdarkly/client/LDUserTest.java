package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public class LDUserTest {

  private JsonPrimitive us = new JsonPrimitive(LDCountryCode.US.getAlpha2());

  @Test
  public void testValidCountryCodeSetsCountry() {
    LDUser user = new LDUser.Builder("key").country(LDCountryCode.US).build();

    assert(user.getCountry().equals(us));
  }


  @Test
  public void testValidCountryCodeStringSetsCountry() {
    LDUser user = new LDUser.Builder("key").country("US").build();

    assert(user.getCountry().equals(us));
  }

  @Test
  public void testValidCountryCode3SetsCountry() {
    LDUser user = new LDUser.Builder("key").country("USA").build();

    assert(user.getCountry().equals(us));
  }

  @Test
  public void testAmbiguousCountryNameSetsCountryWithExactMatch() {
    // "United States" is ambiguous: can also match "United States Minor Outlying Islands"
    LDUser user = new LDUser.Builder("key").country("United States").build();
    assert(user.getCountry().equals(us));
  }

  @Test
  public void testAmbiguousCountryNameSetsCountryWithPartialMatch() {
    // For an ambiguous match, we return the first match
    LDUser user = new LDUser.Builder("key").country("United St").build();
    assert(user.getCountry() != null);
  }


  @Test
  public void testPartialUniqueMatchSetsCountry() {
    LDUser user = new LDUser.Builder("key").country("United States Minor").build();
    assert(user.getCountry().equals(new JsonPrimitive(LDCountryCode.UM.getAlpha2())));
  }

  @Test
  public void testInvalidCountryNameDoesNotSetCountry() {
    LDUser user = new LDUser.Builder("key").country("East Jibip").build();
    assert(user.getCountry() == null);
  }

  @Test
  public void testLDUserJsonSerializationContainsCountryAsTwoDigitCode() {
    Gson gson = new Gson();
    LDUser user = new LDUser.Builder("key").country(LDCountryCode.US).build();

    String jsonStr = gson.toJson(user);

    LDUser deserialized = gson.fromJson(jsonStr, LDUser.class);

    assert(deserialized.getCountry().equals(us));
  }

  @Test
  public void testLDUserCopyWithHiddenAttrsProducesEquivalentLDUserIfNoAttrsAreHidden() {
    LDUser user = new LDUser.Builder("key")
                            .anonymous(true)
                            .avatar("avatar")
                            .country(LDCountryCode.AC)
                            .ip("127.0.0.1")
                            .firstName("bob")
                            .lastName("loblaw")
                            .email("bob@example.com")
                            .custom("foo", 42)
                            .build();

    assert(user.withHiddenAttrs(LDConfig.DEFAULT).equals(user));
  }

  @Test
  public void testLDUserCopyWithHiddenAttrsHidesCorrectAttrs() {
    LDUser user = new LDUser.Builder("key")
        .hiddenCustom("foo", 42)
        .custom("bar", 43)
        .build();

    LDUser hidden = user.withHiddenAttrs(LDConfig.DEFAULT);

    assertNull(hidden.getCustom("foo"));
    assert(hidden.getCustom("bar").equals(new JsonPrimitive(43)));
  }

  @Test public void testLDUserCopyWithHiddenGlobalAttributesHidesCorrectAttrs() {
    LDConfig testConfig = new LDConfig.Builder().hiddenAttrNames("foo", "bar").build();

    LDUser user = new LDUser.Builder("key")
        .hiddenCustom("foo", 42)
        .custom("bar", 43)
        .custom("baz", 44)
        .hiddenCustom("bum", 45)
        .build();

    LDUser hidden = user.withHiddenAttrs(testConfig);

    assertNull(hidden.getCustom("foo"));
    assertNull(hidden.getCustom("bar"));
    assertNull(hidden.getCustom("bum"));
    assert(hidden.getCustom("baz").equals(new JsonPrimitive(44)));

  }
}
