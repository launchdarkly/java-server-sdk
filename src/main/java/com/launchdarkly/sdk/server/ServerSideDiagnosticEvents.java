package com.launchdarkly.sdk.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.internal.events.DiagnosticConfigProperty;
import com.launchdarkly.sdk.internal.events.DiagnosticStore;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.DiagnosticDescription;
import com.launchdarkly.sdk.server.subsystems.HttpConfiguration;

abstract class ServerSideDiagnosticEvents {
  public static DiagnosticStore.SdkDiagnosticParams getSdkDiagnosticParams(
      ClientContext clientContext,
      LDConfig config
      ) {
    return new DiagnosticStore.SdkDiagnosticParams(
        clientContext.getSdkKey(),
        "java-server-sdk",
        Version.SDK_VERSION,
        "java",
        makePlatformData(),
        ImmutableMap.copyOf(clientContext.getHttp().getDefaultHeaders()),
        makeConfigProperties(clientContext, config)
        );
  }

  private static ImmutableList<LDValue> makeConfigProperties(ClientContext clientContext, LDConfig config) {
    ImmutableList.Builder<LDValue> listBuilder = ImmutableList.builder();
    HttpConfiguration httpConfig = clientContext.getHttp();
    
    // Add the top-level properties that are not specific to a particular component type.
    ObjectBuilder builder = LDValue.buildObject();
    builder.put(DiagnosticConfigProperty.CONNECT_TIMEOUT_MILLIS.name, httpConfig.getConnectTimeout().toMillis());
    builder.put(DiagnosticConfigProperty.SOCKET_TIMEOUT_MILLIS.name, httpConfig.getSocketTimeout().toMillis());
    builder.put(DiagnosticConfigProperty.USING_PROXY.name, httpConfig.getProxy() != null);
    builder.put(DiagnosticConfigProperty.USING_PROXY_AUTHENTICATOR.name, httpConfig.getProxyAuthentication() != null);
    builder.put(DiagnosticConfigProperty.START_WAIT_MILLIS.name, config.startWait.toMillis());
    listBuilder.add(builder.build());
    
    // Allow each pluggable component to describe its own relevant properties.
    listBuilder.add(describeComponent(config.dataStore, clientContext, DiagnosticConfigProperty.DATA_STORE_TYPE.name));
    listBuilder.add(describeComponent(config.dataSource, clientContext, null));
    listBuilder.add(describeComponent(config.events, clientContext, null));
    return listBuilder.build();
  }
  
  // Attempts to add relevant configuration properties, if any, from a customizable component:
  // - If the component does not implement DiagnosticDescription, set the defaultPropertyName property to "custom".
  // - If it does implement DiagnosticDescription, call its describeConfiguration() method to get a value.
  // - If the value is a string, then set the defaultPropertyName property to that value.
  // - If the value is an object, then copy all of its properties as long as they are ones we recognize
  //   and have the expected type.
  private static LDValue describeComponent(
      Object component,
      ClientContext clientContext,
      String defaultPropertyName
      ) {
    if (!(component instanceof DiagnosticDescription)) {
      if (defaultPropertyName != null) {
        return LDValue.buildObject().put(defaultPropertyName, "custom").build();
      }
      return LDValue.ofNull();
    }
    LDValue componentDesc = LDValue.normalize(((DiagnosticDescription)component).describeConfiguration(clientContext));
    if (defaultPropertyName == null) {
      return componentDesc;
    }
    return LDValue.buildObject().put(defaultPropertyName,
        componentDesc.isString() ? componentDesc.stringValue() : "custom").build();
  }

  private static LDValue makePlatformData() {
    // We're getting these properties in the server-side-specific logic because they don't return
    // useful values in Android.
    return LDValue.buildObject()
        .put("osName", normalizeOsName(System.getProperty("os.name")))
        .put("javaVendor", System.getProperty("java.vendor"))
        .put("javaVersion", System.getProperty("java.version"))
        .build();
  }
  
  private static String normalizeOsName(String osName) {
    // For our diagnostics data, we prefer the standard names "Linux", "MacOS", and "Windows".
    // "Linux" is already what the JRE returns in Linux. In Windows, we get "Windows 10" etc.
    if (osName != null) {
      if (osName.equals("Mac OS X")) {
        return "MacOS";
      }
      if (osName.startsWith("Windows")) {
        return "Windows";
      }
    }
    return osName;
  }
}
