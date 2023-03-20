package com.launchdarkly.sdk.server.integrations;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import com.launchdarkly.sdk.server.integrations.FileDataSourceBuilder.SourceInfo;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;

abstract class FileDataSourceParsing {
  private FileDataSourceParsing() {}
  
  /**
   * Indicates that the file processor encountered an error in one of the input files. This exception is
   * not surfaced to the host application, it is only logged, and we don't do anything different programmatically
   * with different kinds of exceptions, therefore it has no subclasses.
   */
  @SuppressWarnings("serial")
  static final class FileDataException extends Exception {
    private final SourceInfo source;
    
    public FileDataException(String message, Throwable cause, SourceInfo source) {
      super(message, cause);
      this.source = source;
    }
  
    public FileDataException(String message, Throwable cause) {
      this(message, cause, null);
    }
  
    public String getDescription() {
      StringBuilder s = new StringBuilder();
      if (getMessage() != null) {
        s.append(getMessage());
        s.append(" ");
      }
      s.append("[").append(getCause().toString()).append("]");
      if (source != null) {
        s.append(": ").append(source.toString());
      }
      return s.toString();
    }
  }

  /**
   * The basic data structure that we expect all source files to contain. Note that we don't try to
   * parse the flags or segments at this level; that will be done by {@link FlagFactory}.
   */
  static final class FlagFileRep {
    Map<String, LDValue> flags;
    Map<String, LDValue> flagValues;
    Map<String, LDValue> segments;
    
    FlagFileRep() {}
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
          // COVERAGE: there is no way to simulate this condition in a unit test
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
        // COVERAGE: there is no way to simulate this condition in a unit test
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
      // Using SafeConstructor disables instantiation of arbitrary classes - https://github.com/launchdarkly/java-server-sdk/issues/288
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
      JsonElement jsonRoot = root == null ? new JsonObject() : gson.toJsonTree(root);
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
  static abstract class FlagFactory {
    private FlagFactory() {}
    
    static ItemDescriptor flagFromJson(LDValue jsonTree, int version) {
      return FEATURES.deserialize(replaceVersion(jsonTree, version).toJsonString());
    }
    
    /**
     * Constructs a flag that always returns the same value. This is done by giving it a single
     * variation and setting the fallthrough variation to that.
     */
    static ItemDescriptor flagWithValue(String key, LDValue jsonValue, int version) {
      LDValue o = LDValue.buildObject()
            .put("key", key)
            .put("version", version)
            .put("on", true)
            .put("variations", LDValue.buildArray().add(jsonValue).build())
            .put("fallthrough", LDValue.buildObject().put("variation", 0).build())
            .build();
      // Note that LaunchDarkly normally prevents you from creating a flag with just one variation,
      // but it's the application that validates that; the SDK doesn't care.
      return FEATURES.deserialize(o.toJsonString());
    }
    
    static ItemDescriptor segmentFromJson(LDValue jsonTree, int version) {
      return SEGMENTS.deserialize(replaceVersion(jsonTree, version).toJsonString());
    }
    
    private static LDValue replaceVersion(LDValue objectValue, int version) {
      ObjectBuilder b = LDValue.buildObject();
      for (String key: objectValue.keys()) {
        b.put(key, objectValue.get(key));
      }
      b.put("version", version);
      return b.build();
    }
  }
}
