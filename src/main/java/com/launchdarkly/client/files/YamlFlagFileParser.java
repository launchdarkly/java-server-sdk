package com.launchdarkly.client.files;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;

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
final class YamlFlagFileParser extends FlagFileParser {
  private static final Yaml yaml = new Yaml();
  private static final Gson gson = new Gson();
  private static final JsonFlagFileParser jsonFileParser = new JsonFlagFileParser();
  
  @Override
  public FlagFileRep parse(InputStream input) throws DataLoaderException, IOException {
    Object root;
    try {
      root = yaml.load(input);
    } catch (YAMLException e) {
      throw new DataLoaderException("unable to parse YAML", e);
    }
    JsonElement jsonRoot = gson.toJsonTree(root);
    return jsonFileParser.parseJson(jsonRoot);
  }
}
