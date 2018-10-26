package com.launchdarkly.client.files;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

abstract class FlagFileParser {
  private static final FlagFileParser jsonParser = new JsonFlagFileParser();
  private static final FlagFileParser yamlParser = new YamlFlagFileParser();

  public abstract FlagFileRep parse(InputStream input) throws DataLoaderException, IOException;
  
  public static FlagFileParser selectForContent(byte[] data) {
    Reader r = new InputStreamReader(new ByteArrayInputStream(data));
    return detectJson(r) ? jsonParser : yamlParser;
  }
  
  private static boolean detectJson(Reader r) {
    // A valid JSON file for our purposes must be an object, i.e. it must start with '{'
    while (true) {
      try {
        int ch = r.read();
        if (ch < 0) {
          return false;
        }
        if (ch == '{') {
          return true;
        }
        if (!Character.isWhitespace(ch)) {
          return false;
        }
      } catch (IOException e) {
        return false;
      }
    }
  }
}
