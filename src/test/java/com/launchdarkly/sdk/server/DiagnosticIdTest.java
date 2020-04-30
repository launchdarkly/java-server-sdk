package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.launchdarkly.sdk.server.DiagnosticId;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings("javadoc")
public class DiagnosticIdTest {
  private static final Gson gson = new Gson();

  @Test
  public void hasUUID() {
    DiagnosticId diagnosticId = new DiagnosticId("SDK_KEY");
    assertNotNull(diagnosticId.diagnosticId);
    assertNotNull(UUID.fromString(diagnosticId.diagnosticId));
  }

  @Test
  public void nullKeyIsSafe() {
    // We can't send diagnostics without a key anyway, so we're just validating that the
    // constructor won't crash with a null key
    new DiagnosticId(null);
  }

  @Test
  public void shortKeyIsSafe() {
    DiagnosticId diagnosticId = new DiagnosticId("foo");
    assertEquals("foo", diagnosticId.sdkKeySuffix);
  }

  @Test
  public void keyIsSuffix() {
    DiagnosticId diagnosticId = new DiagnosticId("this_is_a_fake_key");
    assertEquals("ke_key", diagnosticId.sdkKeySuffix);
  }

  @Test
  public void gsonSerialization() {
    DiagnosticId diagnosticId = new DiagnosticId("this_is_a_fake_key");
    JsonObject jsonObject = gson.toJsonTree(diagnosticId).getAsJsonObject();
    assertEquals(2, jsonObject.size());
    String id = jsonObject.getAsJsonPrimitive("diagnosticId").getAsString();
    assertNotNull(UUID.fromString(id));
    assertEquals("ke_key", jsonObject.getAsJsonPrimitive("sdkKeySuffix").getAsString());
  }
}
