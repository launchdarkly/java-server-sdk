package com.launchdarkly.client;


import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class LaunchDarklyClient {
  private final Config config;
  private final HttpClient client;

  public LaunchDarklyClient(String apiKey) {
    this(new Config(apiKey));
  }

  public LaunchDarklyClient(Config config) {
    this.config = config;

    PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
    manager.setMaxTotal(100);

    client = HttpClients.custom().setConnectionManager(manager).build();
  }



}