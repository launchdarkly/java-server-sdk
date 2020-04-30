package com.launchdarkly.sdk.server;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.UserAttribute;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

class EventUserSerialization {

  // Used internally when including users in analytics events, to ensure that private attributes are stripped out.
  static class UserAdapterWithPrivateAttributeBehavior extends TypeAdapter<LDUser> {
    private static final UserAttribute[] BUILT_IN_OPTIONAL_STRING_ATTRIBUTES = new UserAttribute[] {
        UserAttribute.SECONDARY_KEY,
        UserAttribute.IP,
        UserAttribute.EMAIL,
        UserAttribute.NAME,
        UserAttribute.AVATAR,
        UserAttribute.FIRST_NAME,
        UserAttribute.LAST_NAME,
        UserAttribute.COUNTRY
    };
    
    private final EventsConfiguration config;

    public UserAdapterWithPrivateAttributeBehavior(EventsConfiguration config) {
      this.config = config;
    }

    @Override
    public void write(JsonWriter out, LDUser user) throws IOException {
      if (user == null) {
        out.value((String)null);
        return;
      }
      
      // Collect the private attribute names (use TreeSet to make ordering predictable for tests)
      Set<String> privateAttributeNames = new TreeSet<String>();

      out.beginObject();
      // The key can never be private
      out.name("key").value(user.getKey());

      for (UserAttribute attr: BUILT_IN_OPTIONAL_STRING_ATTRIBUTES) {
        LDValue value = user.getAttribute(attr);
        if (!value.isNull()) {
          if (!checkAndAddPrivate(attr, user, privateAttributeNames)) {
            out.name(attr.getName()).value(value.stringValue());
          }
        }
      }
      if (!user.getAttribute(UserAttribute.ANONYMOUS).isNull()) {
        out.name("anonymous").value(user.isAnonymous());
      }
      writeCustomAttrs(out, user, privateAttributeNames);
      writePrivateAttrNames(out, privateAttributeNames);

      out.endObject();
    }

    private void writePrivateAttrNames(JsonWriter out, Set<String> names) throws IOException {
      if (names.isEmpty()) {
        return;
      }
      out.name("privateAttrs");
      out.beginArray();
      for (String name : names) {
        out.value(name);
      }
      out.endArray();
    }

    private boolean checkAndAddPrivate(UserAttribute attribute, LDUser user, Set<String> privateAttrs) {
      boolean result = config.allAttributesPrivate || config.privateAttributes.contains(attribute) || user.isAttributePrivate(attribute);
      if (result) {
        privateAttrs.add(attribute.getName());
      }
      return result;
    }

    private void writeCustomAttrs(JsonWriter out, LDUser user, Set<String> privateAttributeNames) throws IOException {
      boolean beganObject = false;
      for (UserAttribute attribute: user.getCustomAttributes()) {
        if (!checkAndAddPrivate(attribute, user, privateAttributeNames)) {
          if (!beganObject) {
            out.name("custom");
            out.beginObject();
            beganObject = true;
          }
          out.name(attribute.getName());
          LDValue value = user.getAttribute(attribute);
          JsonHelpers.gsonInstance().toJson(value, LDValue.class, out);
        }
      }
      if (beganObject) {
        out.endObject();
      }
    }

    @Override
    public LDUser read(JsonReader in) throws IOException {
      // We never need to unmarshal user objects, so there's no need to implement this
      return null;
    }
  }
}
