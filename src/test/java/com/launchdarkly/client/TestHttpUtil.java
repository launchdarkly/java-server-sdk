package com.launchdarkly.client;

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
  
  static LDConfig.Builder baseConfig(MockWebServer server) {
    URI uri = server.url("").uri();
    return new LDConfig.Builder()
        .baseURI(uri)
        .streamURI(uri)
        .eventsURI(uri);
  }
  
  static class HttpsServerWithSelfSignedCert implements Closeable {
    final MockWebServer server;
    final HeldCertificate cert;
    final SslClient sslClient;
    
    public HttpsServerWithSelfSignedCert() throws IOException, GeneralSecurityException {
      cert = new HeldCertificate.Builder()
        .serialNumber("1")
        .commonName(InetAddress.getByName("localhost").getCanonicalHostName())
        .subjectAlternativeName("localhost")
        .build();
    
      sslClient = new SslClient.Builder()
          .certificateChain(cert.keyPair, cert.certificate)
          .addTrustedCertificate(cert.certificate)
          .build();
      
      server = new MockWebServer();
      server.useHttps(sslClient.socketFactory, false);
      
      server.start();
    }
    
    public URI uri() {
      return server.url("/").uri();
    }
    
    public void close() throws IOException {
      server.close();
    }
  }
}
