package com.launchdarkly.client.flag;

import com.google.gson.JsonPrimitive;
import org.joda.time.DateTime;

import java.util.regex.Pattern;

import static com.launchdarkly.client.flag.Util.jsonPrimitiveToDateTime;

public enum Operator {
  in {
    @Override
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      if (uValue.isString() && cValue.isString()) {
        if (uValue.getAsString().equals(cValue.getAsString()))
          return true;
      }
      if (uValue.isNumber() && cValue.isNumber()) {
        return uValue.getAsDouble() == cValue.getAsDouble();
      }
      DateTime uDateTime = jsonPrimitiveToDateTime(uValue);
      if (uDateTime != null) {
        DateTime cDateTime = jsonPrimitiveToDateTime(cValue);
        if (cDateTime != null) {
          return uDateTime.getMillis() == cDateTime.getMillis();
        }
      }
      return false;
    }
  },
  endsWith {
    @Override
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() && uValue.getAsString().endsWith(cValue.getAsString());
    }
  },
  startsWith {
    @Override
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() && uValue.getAsString().startsWith(cValue.getAsString());
    }
  },
  matches {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() && Pattern.matches(cValue.getAsString(), uValue.getAsString());
    }
  },
  contains {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isString() && cValue.isString() && uValue.getAsString().contains(cValue.getAsString());
    }
  },
  lessThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsDouble() < cValue.getAsDouble();
    }
  },
  lessThanOrEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsDouble() <= cValue.getAsDouble();
    }
  },
  greaterThan {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsDouble() > cValue.getAsDouble();
    }
  },
  greaterThanOrEqual {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      return uValue.isNumber() && cValue.isNumber() && uValue.getAsDouble() >= cValue.getAsDouble();
    }
  },
  before {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      DateTime uDateTime = jsonPrimitiveToDateTime(uValue);
      if (uDateTime != null) {
        DateTime cDateTime = jsonPrimitiveToDateTime(cValue);
        if (cDateTime != null) {
          return uDateTime.isBefore(cDateTime);
        }
      }
      return false;
    }
  },
  after {
    public boolean apply(JsonPrimitive uValue, JsonPrimitive cValue) {
      DateTime uDateTime = jsonPrimitiveToDateTime(uValue);
      if (uDateTime != null) {
        DateTime cDateTime = jsonPrimitiveToDateTime(cValue);
        if (cDateTime != null) {
          return uDateTime.isAfter(cDateTime);
        }
      }
      return false;
    }
  };
  public abstract boolean apply(JsonPrimitive uValue, JsonPrimitive cValue);
}
