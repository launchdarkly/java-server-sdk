package com.launchdarkly.sdk.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.launchdarkly.sdk.server.DiagnosticEvent.Init.DiagnosticSdk;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import org.junit.Test;

import static com.launchdarkly.sdk.server.TestComponents.clientContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@SuppressWarnings("javadoc")
public class DiagnosticSdkTest {
  private static final Gson gson = new Gson();

  private static HttpConfiguration makeHttpConfig(LDConfig config) {
    // the SDK key doesn't matter for these tests
    return clientContext("SDK_KEY", config).getHttp();
  }
  
  @Test
  public void defaultFieldValues() {
    DiagnosticSdk diagnosticSdk = new DiagnosticSdk(makeHttpConfig(LDConfig.DEFAULT));
    assertEquals("java-server-sdk", diagnosticSdk.name);
    assertEquals(Version.SDK_VERSION, diagnosticSdk.version);
    assertNull(diagnosticSdk.wrapperName);
    assertNull(diagnosticSdk.wrapperVersion);
  }

  @Test
  public void getsWrapperValuesFromConfig() {
    LDConfig config = new LDConfig.Builder()
        .http(Components.httpConfiguration().wrapper("Scala", "0.1.0"))
        .build();
    DiagnosticSdk diagnosticSdk = new DiagnosticSdk(makeHttpConfig(config));
    assertEquals("java-server-sdk", diagnosticSdk.name);
    assertEquals(Version.SDK_VERSION, diagnosticSdk.version);
    assertEquals(diagnosticSdk.wrapperName, "Scala");
    assertEquals(diagnosticSdk.wrapperVersion, "0.1.0");
  }

  @Test
  public void gsonSerializationNoWrapper() {
    DiagnosticSdk diagnosticSdk = new DiagnosticSdk(makeHttpConfig(LDConfig.DEFAULT));
    JsonObject jsonObject = gson.toJsonTree(diagnosticSdk).getAsJsonObject();
    assertEquals(2, jsonObject.size());
    assertEquals("java-server-sdk", jsonObject.getAsJsonPrimitive("name").getAsString());
    assertEquals(Version.SDK_VERSION, jsonObject.getAsJsonPrimitive("version").getAsString());
  }

  @Test
  public void gsonSerializationWithWrapper() {
    LDConfig config = new LDConfig.Builder()
        .http(Components.httpConfiguration().wrapper("Scala", "0.1.0"))
        .build();
    DiagnosticSdk diagnosticSdk = new DiagnosticSdk(makeHttpConfig(config));
    JsonObject jsonObject = gson.toJsonTree(diagnosticSdk).getAsJsonObject();
    assertEquals(4, jsonObject.size());
    assertEquals("java-server-sdk", jsonObject.getAsJsonPrimitive("name").getAsString());
    assertEquals(Version.SDK_VERSION, jsonObject.getAsJsonPrimitive("version").getAsString());
    assertEquals("Scala", jsonObject.getAsJsonPrimitive("wrapperName").getAsString());
    assertEquals("0.1.0", jsonObject.getAsJsonPrimitive("wrapperVersion").getAsString());
  }
}
