package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.server.interfaces.ApplicationInfo;
import com.launchdarkly.sdk.server.interfaces.HttpAuthentication;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.collect.Iterables.transform;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

abstract class Util {
  private Util() {}
  
  static final Authenticator okhttpAuthenticatorFromHttpAuthStrategy(final HttpAuthentication strategy) {
    return new Authenticator() {
      public Request authenticate(Route route, Response response) throws IOException {
        if (response.request().header("Proxy-Authorization") != null) {
          return null; // Give up, we've already failed to authenticate
        }
        Iterable<HttpAuthentication.Challenge> challenges = transform(response.challenges(),
            c -> new HttpAuthentication.Challenge(c.scheme(), c.realm()));
        String credential = strategy.provideAuthorization(challenges); 
        return response.request().newBuilder()
            .header("Proxy-Authorization", credential)
            .build();
      }
    };  
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
