package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

public class LDUserTest {

  private JsonPrimitive us = new JsonPrimitive(LDCountryCode.US.getAlpha2());

  @Test
  public void testLDUserConstructor() {
    LDUser user = new LDUser.Builder("key")
    .secondary("secondary")
    .ip("127.0.0.1")
    .firstName("Bob")
    .lastName("Loblaw")
    .email("bob@example.com")
    .name("Bob Loblaw")
    .avatar("image")
    .anonymous(false)
    .country("US")
    .build();
    
    assert(user.equals(new LDUser.Builder(user).build()));
  }

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
}
