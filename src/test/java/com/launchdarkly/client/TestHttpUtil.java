package com.launchdarkly.client;

import com.launchdarkly.client.integrations.PollingDataSourceBuilder;
import com.launchdarkly.client.integrations.StreamingDataSourceBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

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
    return Components.streamingDataSource().baseURI(server.url("").uri());
  }
  
  static PollingDataSourceBuilder basePollingConfig(MockWebServer server) {
    return Components.pollingDataSource().baseURI(server.url("").uri());
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
    final SSLSocketFactory socketFactory;
    final X509TrustManager trustManager;
    
    public ServerWithCert() throws IOException, GeneralSecurityException {
      String hostname = InetAddress.getByName("localhost").getCanonicalHostName();
      
      cert = new HeldCertificate.Builder()
        .serialNumber(BigInteger.ONE)
        .certificateAuthority(1)
        .commonName(hostname)
        .addSubjectAlternativeName(hostname)
        .rsa2048()
        .build();

      HandshakeCertificates hc = new HandshakeCertificates.Builder()
        .addPlatformTrustedCertificates()
        .heldCertificate(cert)
        .addTrustedCertificate(cert.certificate())
        .build();
      socketFactory = hc.sslSocketFactory();
      trustManager = hc.trustManager();
      
      server = new MockWebServer();
      server.useHttps(socketFactory, false);
    }
    
    public URI uri() {
      return server.url("/").uri();
    }
    
    public void close() throws IOException {
      server.close();
    }
  }
}
