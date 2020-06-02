package com.launchdarkly.sdk.server.interfaces;

import com.launchdarkly.sdk.server.interfaces.HttpAuthentication.Challenge;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("javadoc")
public class HttpAuthenticationTypesTest {
  @Test
  public void challengeProperties() {
    Challenge c = new Challenge("Basic", "realm");
    assertThat(c.getScheme(), equalTo("Basic"));
    assertThat(c.getRealm(), equalTo("realm"));
  }
}
