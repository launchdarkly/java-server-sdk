package com.launchdarkly.client.files;

import com.google.gson.JsonElement;

import java.util.Map;

/**
 * The basic data structure that we expect all source files to contain. Note that we don't try to
 * parse the flags or segments at this level; that will be done by {@link FlagFactory}.
 */
final class FlagFileRep {
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
