package com.launchdarkly.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.launchdarkly.client.DiagnosticEvent.Init.DiagnosticSdk;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class DiagnosticSdkTest {
  private static final Gson gson = new Gson();

  @Test
  public void defaultFieldValues() {
    DiagnosticSdk diagnosticSdk = new DiagnosticSdk(new LDConfig.Builder().build());
    assertEquals("java-server-sdk", diagnosticSdk.name);
    assertEquals(LDClient.CLIENT_VERSION, diagnosticSdk.version);
    assertNull(diagnosticSdk.wrapperName);
    assertNull(diagnosticSdk.wrapperVersion);
  }

  @Test
  public void getsWrapperValuesFromConfig() {
    LDConfig config = new LDConfig.Builder()
        .wrapperName("Scala")
        .wrapperVersion("0.1.0")
        .build();
    DiagnosticSdk diagnosticSdk = new DiagnosticSdk(config);
    assertEquals("java-server-sdk", diagnosticSdk.name);
    assertEquals(LDClient.CLIENT_VERSION, diagnosticSdk.version);
    assertEquals(diagnosticSdk.wrapperName, "Scala");
    assertEquals(diagnosticSdk.wrapperVersion, "0.1.0");
  }

  @Test
  public void gsonSerializationNoWrapper() {
    DiagnosticSdk diagnosticSdk = new DiagnosticSdk(new LDConfig.Builder().build());
    JsonObject jsonObject = gson.toJsonTree(diagnosticSdk).getAsJsonObject();
    assertEquals(2, jsonObject.size());
    assertEquals("java-server-sdk", jsonObject.getAsJsonPrimitive("name").getAsString());
    assertEquals(LDClient.CLIENT_VERSION, jsonObject.getAsJsonPrimitive("version").getAsString());
  }

  @Test
  public void gsonSerializationWithWrapper() {
    LDConfig config = new LDConfig.Builder()
        .wrapperName("Scala")
        .wrapperVersion("0.1.0")
        .build();
    DiagnosticSdk diagnosticSdk = new DiagnosticSdk(config);
    JsonObject jsonObject = gson.toJsonTree(diagnosticSdk).getAsJsonObject();
    assertEquals(4, jsonObject.size());
    assertEquals("java-server-sdk", jsonObject.getAsJsonPrimitive("name").getAsString());
    assertEquals(LDClient.CLIENT_VERSION, jsonObject.getAsJsonPrimitive("version").getAsString());
    assertEquals("Scala", jsonObject.getAsJsonPrimitive("wrapperName").getAsString());
    assertEquals("0.1.0", jsonObject.getAsJsonPrimitive("wrapperVersion").getAsString());
  }
}
