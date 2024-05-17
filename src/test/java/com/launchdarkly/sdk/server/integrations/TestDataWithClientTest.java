package com.launchdarkly.sdk.server.integrations;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.ErrorInfo;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("javadoc")
public class TestDataWithClientTest {
  private static final String SDK_KEY = "sdk-key";
  
  private TestData td = TestData.dataSource();
  private LDConfig config = new LDConfig.Builder()
      .dataSource(td)
      .events(Components.noEvents())
      .build();
  
  @Test
  public void initializesWithEmptyData() throws Exception {
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.isInitialized(), is(true));
    }
  }

  @Test
  public void initializesWithFlag() throws Exception {
    td.update(td.flag("flag").on(true));

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.boolVariation("flag", LDContext.create("user"), false), is(true));
    }
  }
  
  @Test
  public void updatesFlag() throws Exception {
    td.update(td.flag("flag").on(false));
    
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.boolVariation("flag", LDContext.create("user"), false), is(false));
      
      td.update(td.flag("flag").on(true));
      
      assertThat(client.boolVariation("flag", LDContext.create("user"), false), is(true));
    }
  }

  @Test
  public void deletesFlag() throws Exception {
    td.update(td.flag("flag").on(true));

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.boolVariation("flag", LDContext.create("user"), false), is(true));

      td.delete("flag");

      final EvaluationDetail<Boolean> detail = client.boolVariationDetail("flag", LDContext.create("user"), false);
      assertThat(detail.getValue(), is(false));
      assertThat(detail.isDefaultValue(), is(true));
      assertThat(detail.getReason().getErrorKind(), is(EvaluationReason.ErrorKind.FLAG_NOT_FOUND));
    }
  }

  @Test
  public void usesTargets() throws Exception {
    td.update(td.flag("flag").fallthroughVariation(false).variationForUser("user1", true));

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.boolVariation("flag", LDContext.create("user1"), false), is(true));
      assertThat(client.boolVariation("flag", LDContext.create("user2"), false), is(false));
    }
  }

  @Test
  public void usesRules() throws Exception {
    td.update(td.flag("flag").fallthroughVariation(false)
        .ifMatch("name", LDValue.of("Lucy")).thenReturn(true)
        .ifMatch("name", LDValue.of("Mina")).thenReturn(true));

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.boolVariation("flag", LDContext.builder("user1").name("Lucy").build(), false), is(true));
      assertThat(client.boolVariation("flag", LDContext.builder("user2").name("Mina").build(), false), is(true));
      assertThat(client.boolVariation("flag", LDContext.builder("user3").name("Quincy").build(), false), is(false));
    }
  }

  @Test
  public void nonBooleanFlags() throws Exception {
    td.update(td.flag("flag").variations(LDValue.of("red"), LDValue.of("green"), LDValue.of("blue"))
        .offVariation(0).fallthroughVariation(2)
        .variationForUser("user1", 1)
        .ifMatch("name", LDValue.of("Mina")).thenReturn(1));

    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.stringVariation("flag", LDContext.builder("user1").name("Lucy").build(), ""), equalTo("green"));
      assertThat(client.stringVariation("flag", LDContext.builder("user2").name("Mina").build(), ""), equalTo("green"));
      assertThat(client.stringVariation("flag", LDContext.builder("user3").name("Quincy").build(), ""), equalTo("blue"));
      
      td.update(td.flag("flag").on(false));

      assertThat(client.stringVariation("flag", LDContext.builder("user1").name("Lucy").build(), ""), equalTo("red"));
    }
  }
  
  @Test
  public void canUpdateStatus() throws Exception {
    try (LDClient client = new LDClient(SDK_KEY, config)) {
      assertThat(client.getDataSourceStatusProvider().getStatus().getState(), equalTo(State.VALID));
      
      ErrorInfo ei = ErrorInfo.fromHttpError(500);
      td.updateStatus(State.INTERRUPTED, ei);
      
      assertThat(client.getDataSourceStatusProvider().getStatus().getState(), equalTo(State.INTERRUPTED));
      assertThat(client.getDataSourceStatusProvider().getStatus().getLastError(), equalTo(ei));
    }
  }
  
  @Test
  public void dataSourcePropagatesToMultipleClients() throws Exception {
    td.update(td.flag("flag").on(true));

    try (LDClient client1 = new LDClient(SDK_KEY, config)) {
      try (LDClient client2 = new LDClient(SDK_KEY, config)) {
        assertThat(client1.boolVariation("flag", LDContext.create("user"), false), is(true));
        assertThat(client2.boolVariation("flag", LDContext.create("user"), false), is(true));
        
        td.update(td.flag("flag").on(false));

        assertThat(client1.boolVariation("flag", LDContext.create("user"), false), is(false));
        assertThat(client2.boolVariation("flag", LDContext.create("user"), false), is(false));
      }
    }
  }
}
