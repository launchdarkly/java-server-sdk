package com.launchdarkly.client;

import com.launchdarkly.client.integrations.PollingDataSourceBuilder;
import com.launchdarkly.client.integrations.StreamingDataSourceBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.GeneralSecurityException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.internal.tls.HeldCertificate;
import okhttp3.mockwebserver.internal.tls.SslClient;

class TestHttpUtil {
  static MockWebServer makeStartedServer(MockResponse... responses) throws IOException {
    MockWebServer server = new MockWebServer();
    for (MockResponse r: responses) {
      server.enqueue(r);
    }
    server.start();
    return server;
  }
  
  static ServerWithCert httpsServerWithSelfSignedCert(MockResponse... responses) throws IOException, GeneralSecurityException {
    ServerWithCert ret = new ServerWithCert();
    for (MockResponse r: responses) {
      ret.server.enqueue(r);
    }
    ret.server.start();
    return ret;
  }

  static StreamingDataSourceBuilder baseStreamingConfig(MockWebServer server) {
    return Components.streamingDataSource().baseUri(server.url("").uri());
  }
  
  static PollingDataSourceBuilder basePollingConfig(MockWebServer server) {
    return Components.pollingDataSource().baseUri(server.url("").uri());
  }
  
  static MockResponse jsonResponse(String body) {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
  
  static MockResponse eventStreamResponse(String data) {
    return new MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setChunkedBody(data, 1000);
  }
  
  static class ServerWithCert implements Closeable {
    final MockWebServer server;
    final HeldCertificate cert;
    final SslClient sslClient;
    
    public ServerWithCert() throws IOException, GeneralSecurityException {
      String hostname = InetAddress.getByName("localhost").getCanonicalHostName();
      
      cert = new HeldCertificate.Builder()
        .serialNumber("1")
        .commonName(hostname)
        .subjectAlternativeName(hostname)
        .build();
    
      sslClient = new SslClient.Builder()
          .certificateChain(cert.keyPair, cert.certificate)
          .addTrustedCertificate(cert.certificate)
          .build();
      
      server = new MockWebServer();
      server.useHttps(sslClient.socketFactory, false);
    }
    
    public URI uri() {
      return server.url("/").uri();
    }
    
    public void close() throws IOException {
      server.close();
    }
  }
}
