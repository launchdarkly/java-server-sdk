package com.launchdarkly.client.integrations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.launchdarkly.client.DataModel;
import com.launchdarkly.client.interfaces.VersionedData;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Map;

abstract class FileDataSourceParsing {
  /**
   * Indicates that the file processor encountered an error in one of the input files. This exception is
   * not surfaced to the host application, it is only logged, and we don't do anything different programmatically
   * with different kinds of exceptions, therefore it has no subclasses.
   */
  @SuppressWarnings("serial")
  static final class FileDataException extends Exception {
    private final Path filePath;
    
    public FileDataException(String message, Throwable cause, Path filePath) {
      super(message, cause);
      this.filePath = filePath;
    }
  
    public FileDataException(String message, Throwable cause) {
      this(message, cause, null);
    }
  
    public Path getFilePath() {
      return filePath;
    }
    
    public String getDescription() {
      StringBuilder s = new StringBuilder();
      if (getMessage() != null) {
        s.append(getMessage());
        if (getCause() != null) {
          s.append(" ");
        }
      }
      if (getCause() != null) {
        s.append(" [").append(getCause().toString()).append("]");
      }
      if (filePath != null) {
        s.append(": ").append(filePath);
      }
      return s.toString();
    }
  }

  /**
   * The basic data structure that we expect all source files to contain. Note that we don't try to
   * parse the flags or segments at this level; that will be done by {@link FlagFactory}.
   */
  static final class FlagFileRep {
    Map<String, JsonElement> flags;
    Map<String, JsonElement> flagValues;
    Map<String, JsonElement> segments;
    
    FlagFileRep() {}
  
    FlagFileRep(Map<String, JsonElement> flags, Map<String, JsonElement> flagValues, Map<String, JsonElement> segments) {
      this.flags = flags;
      this.flagValues = flagValues;
      this.segments = segments;
    }
  }

  static abstract class FlagFileParser {
    private static final FlagFileParser jsonParser = new JsonFlagFileParser();
    private static final FlagFileParser yamlParser = new YamlFlagFileParser();
  
    public abstract FlagFileRep parse(InputStream input) throws FileDataException, IOException;
    
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

  static final class JsonFlagFileParser extends FlagFileParser {
    private static final Gson gson = new Gson();
  
    @Override
    public FlagFileRep parse(InputStream input) throws FileDataException, IOException {
      try {
        return parseJson(gson.fromJson(new InputStreamReader(input), JsonElement.class));
      } catch (JsonSyntaxException e) {
        throw new FileDataException("cannot parse JSON", e);
      }    
    }
    
    public FlagFileRep parseJson(JsonElement tree) throws FileDataException, IOException {
      try {
        return gson.fromJson(tree, FlagFileRep.class);
      } catch (JsonSyntaxException e) {
        throw new FileDataException("cannot parse JSON", e);
      }    
    }
  }

  /**
   * Parses a FlagFileRep from a YAML file. Two notes about this implementation:
   * <p>
   * 1. We already have logic for parsing (and building) flags using Gson, and would rather not repeat
   * that logic. So, rather than telling SnakeYAML to parse the file directly into a FlagFileRep object -
   * and providing SnakeYAML-specific methods for building flags - we are just parsing the YAML into
   * simple Java objects and then feeding that data into the Gson parser. This is admittedly inefficient,
   * but it also means that we don't have to worry about any differences between how Gson unmarshals an
   * object and how the YAML parser does it. We already know Gson does the right thing for the flag and
   * segment classes, because that's what we use in the SDK.
   * <p>
   * 2. Ideally, it should be possible to have just one parser, since any valid JSON document is supposed
   * to also be parseable as YAML. However, at present, that doesn't work:
   * <ul>
   * <li> SnakeYAML (1.19) rejects many valid JSON documents due to simple things like whitespace.
   * Apparently this is due to supporting only YAML 1.1, not YAML 1.2 which has full JSON support.
   * <li> snakeyaml-engine (https://bitbucket.org/asomov/snakeyaml-engine) says it can handle any JSON,
   * but it's only for Java 8 and above.
   * <li> YamlBeans (https://github.com/EsotericSoftware/yamlbeans) only works right if you're parsing
   * directly into a Java bean instance (which FeatureFlag is not). If you try the "parse to simple
   * Java types (and then feed them into Gson)" approach, it does not correctly parse non-string types
   * (i.e. it treats true as "true"). (https://github.com/EsotericSoftware/yamlbeans/issues/7)
   * </ul>
   */
  static final class YamlFlagFileParser extends FlagFileParser {
    private static final Yaml yaml = new Yaml();
    private static final Gson gson = new Gson();
    private static final JsonFlagFileParser jsonFileParser = new JsonFlagFileParser();
    
    @Override
    public FlagFileRep parse(InputStream input) throws FileDataException, IOException {
      Object root;
      try {
        root = yaml.load(input);
      } catch (YAMLException e) {
        throw new FileDataException("unable to parse YAML", e);
      }
      JsonElement jsonRoot = gson.toJsonTree(root);
      return jsonFileParser.parseJson(jsonRoot);
    }
  }

  /**
   * Creates flag or segment objects from raw JSON.
   * 
   * Note that the {@code FeatureFlag} and {@code Segment} classes are not public in the Java
   * client, so we refer to those class objects indirectly via {@code VersionedDataKind}; and
   * if we want to construct a flag from scratch, we can't use the constructor but instead must
   * build some JSON and then parse that.
   */
  static final class FlagFactory {
    private static final Gson gson = new Gson();
  
    static VersionedData flagFromJson(String jsonString) {
      return flagFromJson(gson.fromJson(jsonString, JsonElement.class));
    }
    
    static VersionedData flagFromJson(JsonElement jsonTree) {
      return gson.fromJson(jsonTree, DataModel.DataKinds.FEATURES.getItemClass());
    }
    
    /**
     * Constructs a flag that always returns the same value. This is done by giving it a single
     * variation and setting the fallthrough variation to that.
     */
    static VersionedData flagWithValue(String key, JsonElement value) {
      JsonElement jsonValue = gson.toJsonTree(value);
      JsonObject o = new JsonObject();
      o.addProperty("key", key);
      o.addProperty("on", true);
      JsonArray vs = new JsonArray();
      vs.add(jsonValue);
      o.add("variations", vs);
      // Note that LaunchDarkly normally prevents you from creating a flag with just one variation,
      // but it's the application that validates that; the SDK doesn't care.
      JsonObject ft = new JsonObject();
      ft.addProperty("variation", 0);
      o.add("fallthrough", ft);
      return flagFromJson(o);
    }
    
    static VersionedData segmentFromJson(String jsonString) {
      return segmentFromJson(gson.fromJson(jsonString, JsonElement.class));
    }
    
    static VersionedData segmentFromJson(JsonElement jsonTree) {
      return gson.fromJson(jsonTree, DataModel.DataKinds.SEGMENTS.getItemClass());
    }
  }
}
