package com.launchdarkly.client.files;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

final class JsonFlagFileParser extends FlagFileParser {
  private static final Gson gson = new Gson();

  @Override
  public FlagFileRep parse(InputStream input) throws DataLoaderException, IOException {
    try {
      return parseJson(gson.fromJson(new InputStreamReader(input), JsonElement.class));
    } catch (JsonSyntaxException e) {
      throw new DataLoaderException("cannot parse JSON", e);
    }    
  }
  
  public FlagFileRep parseJson(JsonElement tree) throws DataLoaderException, IOException {
    try {
      return gson.fromJson(tree, FlagFileRep.class);
    } catch (JsonSyntaxException e) {
      throw new DataLoaderException("cannot parse JSON", e);
    }    
  }
}
