package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;
import com.launchdarkly.sdk.server.interfaces.HttpConfiguration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Iterables.transform;

import okhttp3.Authenticator;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

abstract class Util {
  private Util() {}
  
  static Headers.Builder getHeadersBuilderFor(HttpConfiguration config) {
    Headers.Builder builder = new Headers.Builder();
    for (Map.Entry<String, String> kv: config.getDefaultHeaders()) {
      builder.add(kv.getKey(), kv.getValue());
    }
    return builder;
  }
  
  // This is specifically testing whether the string would be considered a valid HTTP header value
  // *by the OkHttp client*. The actual HTTP spec does not prohibit characters >= 127; OkHttp's
  // check is overly strict, as was pointed out in https://github.com/square/okhttp/issues/2016.
  // But all OkHttp 3.x and 4.x versions so far have continued to enforce that check. Control
  // characters other than a tab are always illegal.
  //
  // The value we're mainly concerned with is the SDK key (Authorization header). If an SDK key
  // accidentally has (for instance) a newline added to it, we don't want to end up having OkHttp
  // throw an exception mentioning the value, which might get logged (https://github.com/square/okhttp/issues/6738).
  static boolean isAsciiHeaderValue(String value) {
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if ((ch < 0x20 || ch > 0x7e) && ch != '\t') {
        return false;
      }
    }
    return true;
  }
  
  static void configureHttpClientBuilder(HttpConfiguration config, OkHttpClient.Builder builder) {
    builder.connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
      .connectTimeout(config.getConnectTimeout())
      .readTimeout(config.getSocketTimeout())
      .writeTimeout(config.getSocketTimeout())
      .retryOnConnectionFailure(false); // we will implement our own retry logic
      
    if (config.getSocketFactory() != null) {
      builder.socketFactory(config.getSocketFactory());
    }

    if (config.getSslSocketFactory() != null) {
      builder.sslSocketFactory(config.getSslSocketFactory(), config.getTrustManager());
    }

    if (config.getProxy() != null) {
      builder.proxy(config.getProxy());
      if (config.getProxyAuthentication() != null) {
        builder.proxyAuthenticator(okhttpAuthenticatorFromHttpAuthStrategy(
            config.getProxyAuthentication(),
            "Proxy-Authentication",
            "Proxy-Authorization"
        ));
      }
    }
  }
  
  static final Authenticator okhttpAuthenticatorFromHttpAuthStrategy(final HttpAuthentication strategy,
      final String challengeHeaderName, final String responseHeaderName) {
    return new Authenticator() {
      public Request authenticate(Route route, Response response) throws IOException {
        if (response.request().header(responseHeaderName) != null) {
          return null; // Give up, we've already failed to authenticate
        }
        Iterable<HttpAuthentication.Challenge> challenges = transform(response.challenges(),
            c -> new HttpAuthentication.Challenge(c.scheme(), c.realm()));
        String credential = strategy.provideAuthorization(challenges); 
        return response.request().newBuilder()
            .header(responseHeaderName, credential)
            .build();
      }
    };  
  }
  
  static void shutdownHttpClient(OkHttpClient client) {
    if (client.dispatcher() != null) {
      client.dispatcher().cancelAll();
      if (client.dispatcher().executorService() != null) {
        client.dispatcher().executorService().shutdown();
      }
    }
    if (client.connectionPool() != null) {
      client.connectionPool().evictAll();
    }
    if (client.cache() != null) {
      try {
        client.cache().close();
      } catch (Exception e) {}
    }
  }
  
  /**
   * Tests whether an HTTP error status represents a condition that might resolve on its own if we retry.
   * @param statusCode the HTTP status
   * @return true if retrying makes sense; false if it should be considered a permanent failure
   */
  static boolean isHttpErrorRecoverable(int statusCode) {
    if (statusCode >= 400 && statusCode < 500) {
      switch (statusCode) {
      case 400: // bad request
      case 408: // request timeout
      case 429: // too many requests
        return true;
      default:
        return false; // all other 4xx errors are unrecoverable
      }
    }
    return true;
  }
  
  /**
   * Logs an HTTP error or network error at the appropriate level and determines whether it is recoverable
   * (as defined by {@link #isHttpErrorRecoverable(int)}).
   *  
   * @param logger the logger to log to
   * @param errorDesc description of the error
   * @param errorContext a phrase like "when doing such-and-such"
   * @param statusCode HTTP status code, or 0 for a network error
   * @param recoverableMessage a phrase like "will retry" to use if the error is recoverable
   * @return true if the error is recoverable
   */
  static boolean checkIfErrorIsRecoverableAndLog(
      LDLogger logger,
      String errorDesc,
      String errorContext,
      int statusCode,
      String recoverableMessage
      ) {
    if (statusCode > 0 && !isHttpErrorRecoverable(statusCode)) {
      logger.error("Error {} (giving up permanently): {}", errorContext, errorDesc);
      return false;
    } else {
      logger.warn("Error {} ({}): {}", errorContext, recoverableMessage, errorDesc);
      return true;
    }
  }
  
  static String httpErrorDescription(int statusCode) {
    return "HTTP error " + statusCode +
        (statusCode == 401 || statusCode == 403 ? " (invalid SDK key)" : "");
  }
  
  static String describeDuration(Duration d) {
    if (d.toMillis() % 1000 == 0) {
      if (d.toMillis() % 60000 == 0) {
        return d.toMinutes() + (d.toMinutes() == 1 ? " minute" : " minutes");
      } else {
        long sec = d.toMillis() / 1000;
        return sec + (sec == 1 ? " second" : " seconds");
      }
    }
    return d.toMillis() + " milliseconds";
  }
  
  static void deleteDirectory(Path path) {
    try {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          try {
            Files.delete(file);
          } catch (IOException e) {}
          return FileVisitResult.CONTINUE;
        }
        
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
          try {
            Files.delete(dir);
          } catch (IOException e) {}
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {}
  }
  
  static URI concatenateUriPath(URI baseUri, String path) {
    String uriStr = baseUri.toString();
    String addPath = path.startsWith("/") ? path.substring(1) : path;
    return URI.create(uriStr + (uriStr.endsWith("/") ? "" : "/") + addPath);
  }

  // Tag values must not be empty, and only contain letters, numbers, `.`, `_`, or `-`.
  private static Pattern TAG_VALUE_REGEX = Pattern.compile("^[\\w.-]+$");

  /**
   * Builds the "X-LaunchDarkly-Tags" HTTP header out of the configured application info.
   *
   * @param applicationInfo the application metadata
   * @return a space-separated string of tags, e.g. "application-id/authentication-service application-version/1.0.0"
   */
  static String applicationTagHeader(ApplicationInfo applicationInfo, LDLogger logger) {
    String[][] tags = {
      {"applicationId", "application-id", applicationInfo.getApplicationId()},
      {"applicationVersion", "application-version", applicationInfo.getApplicationVersion()},
    };
    List<String> parts = new ArrayList<>();
    for (String[] row : tags) {
      String javaKey = row[0];
      String tagKey = row[1];
      String tagVal = row[2];
      if (tagVal == null) {
        continue;
      }
      if (!TAG_VALUE_REGEX.matcher(tagVal).matches()) {
        logger.warn("Value of ApplicationInfo.{} contained invalid characters and was discarded", javaKey);
        continue;
      }
      if (tagVal.length() > 64) {
        logger.warn("Value of ApplicationInfo.{} was longer than 64 characters and was discarded", javaKey);
        continue;
      }
      parts.add(tagKey + "/" + tagVal);
    }
    return String.join(" ", parts);
  }
}
