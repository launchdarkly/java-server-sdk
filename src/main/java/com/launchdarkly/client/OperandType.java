package com.launchdarkly.client;

import com.google.gson.JsonPrimitive;
import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.Semver.SemverType;
import com.vdurmont.semver4j.SemverException;

/**
 * Operator value that can be applied to {@link JsonPrimitive} objects. Incompatible types or other errors
 * will always yield false. This enum can be directly deserialized from JSON, avoiding the need for a mapping
 * of strings to operators.
 */
enum OperandType {
  string,
  number,
  date,
  semVer;
  
  public static OperandType bestGuess(JsonPrimitive value) {
    return value.isNumber() ? number : string;
  }
  
  public Object getValueAsType(JsonPrimitive value) {
    switch (this) {
    case string:
      return value.getAsString();
    case number:
      if (value.isNumber()) {
        try {
          return value.getAsDouble();
        } catch (NumberFormatException e) {
        }
      }
      return null;
    case date:
      return Util.jsonPrimitiveToDateTime(value);
    case semVer:
      try {
        Semver sv = new Semver(value.getAsString(), SemverType.LOOSE); 
        // LOOSE means only the major version is required.  But comparisons between loose and strictly
        // compliant versions don't work properly, so we always convert to a strict version (i.e. fill
        // in the minor/patch versions with zeroes if they were absent).  Note that if we ever switch
        // to a different semver library that doesn't have exactly the same "loose" mode, we will need
        // to preprocess the string before parsing to get the same behavior. 
        return sv.toStrict();
      } catch (SemverException e) {
        return null;
      }
    default:
      return null;
    }
  }
}
