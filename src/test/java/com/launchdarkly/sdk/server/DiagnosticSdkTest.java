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
    LDConfig config1 = new LDConfig.Builder()
        .http(Components.httpConfiguration().wrapper("Scala", "0.1.0"))
        .build();
    DiagnosticSdk diagnosticSdk1 = new DiagnosticSdk(makeHttpConfig(config1));
    assertEquals("java-server-sdk", diagnosticSdk1.name);
    assertEquals(Version.SDK_VERSION, diagnosticSdk1.version);
    assertEquals(diagnosticSdk1.wrapperName, "Scala");
    assertEquals(diagnosticSdk1.wrapperVersion, "0.1.0");

    LDConfig config2 = new LDConfig.Builder()
        .http(Components.httpConfiguration().wrapper("Scala", null))
        .build();
    DiagnosticSdk diagnosticSdk2 = new DiagnosticSdk(makeHttpConfig(config2));
    assertEquals(diagnosticSdk2.wrapperName, "Scala");
    assertNull(diagnosticSdk2.wrapperVersion);
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
  
  @Test
  public void platformOsNames() {
    String realOsName = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Mac OS X");
      assertEquals("MacOS", new DiagnosticEvent.Init.DiagnosticPlatform().osName);
      
      System.setProperty("os.name", "Windows 10");
      assertEquals("Windows", new DiagnosticEvent.Init.DiagnosticPlatform().osName);
      
      System.setProperty("os.name", "Linux");
      assertEquals("Linux", new DiagnosticEvent.Init.DiagnosticPlatform().osName);

      System.clearProperty("os.name");
      assertNull(new DiagnosticEvent.Init.DiagnosticPlatform().osName);
    } finally {
      System.setProperty("os.name", realOsName);
    }
  }
}
