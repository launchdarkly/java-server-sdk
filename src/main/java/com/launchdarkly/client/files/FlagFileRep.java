package com.launchdarkly.client.files;

import com.launchdarkly.client.value.LDValue;

import java.util.Map;

/**
 * The basic data structure that we expect all source files to contain. Note that we don't try to
 * parse the flags or segments at this level; that will be done by {@link FlagFactory}.
 */
final class FlagFileRep {
  Map<String, LDValue> flags;
  Map<String, LDValue> flagValues;
  Map<String, LDValue> segments;
  
  FlagFileRep() {}

  FlagFileRep(Map<String, LDValue> flags, Map<String, LDValue> flagValues, Map<String, LDValue> segments) {
    this.flags = flags;
    this.flagValues = flagValues;
    this.segments = segments;
  }
}
