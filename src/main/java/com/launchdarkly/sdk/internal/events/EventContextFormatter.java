package com.launchdarkly.sdk.internal.events;

import com.google.gson.stream.JsonWriter;
import com.launchdarkly.sdk.AttributeRef;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.launchdarkly.sdk.internal.GsonHelpers.gsonInstance;

/**
 * Implements serialization of contexts within JSON event data. This uses a similar schema to the
 * regular context JSON schema (i.e. what you get if you call JsonSerialization.serialize() on an
 * LDContext), but not quite the same, because it transforms the context to redact any attributes
 * (or subproperties of attributes that are objects) that were designated as private, accumulating
 * a list of the names of these in _meta.redactedAttributes.
 * <p>
 * This implementation is optimized to avoid unnecessary work in the typical use case where there
 * aren't any private attributes.
 */
class EventContextFormatter {
  private final boolean allAttributesPrivate;
  private final AttributeRef[] globalPrivateAttributes;

  EventContextFormatter(boolean allAttributesPrivate, AttributeRef[] globalPrivateAttributes) {
    this.allAttributesPrivate = allAttributesPrivate;
    this.globalPrivateAttributes = globalPrivateAttributes == null ? new AttributeRef[0] : globalPrivateAttributes;
  }
  
  public void write(LDContext c, JsonWriter w) throws IOException {
    if (c.isMultiple()) {
      w.beginObject();
      w.name("kind").value("multi");
      for (int i = 0; i < c.getIndividualContextCount(); i++) {
        LDContext c1 = c.getIndividualContext(i);
        w.name(c1.getKind().toString());
        writeSingleKind(c1, w, false);
      }
      w.endObject();
    } else {
      writeSingleKind(c, w, true);
    }
  }
  
  private void writeSingleKind(LDContext c, JsonWriter w, boolean includeKind) throws IOException {
    w.beginObject();
    
    // kind, key, and anonymous are never redacted
    if (includeKind) {
      w.name("kind").value(c.getKind().toString());
    }
    w.name("key").value(c.getKey());
    if (c.isAnonymous()) {
      w.name("anonymous").value(true);
    }
    
    List<String> redacted = null;
    
    if (c.getName() != null) {
      if (isAttributeEntirelyPrivate(c, "name")) {
        redacted = addOrCreate(redacted, "name");
      } else {
        w.name("name").value(c.getName());
      }
    }
    
    for (String attrName: c.getCustomAttributeNames()) {
      redacted = writeOrRedactAttribute(w, c, attrName, c.getValue(attrName), redacted);
    }
    
    boolean haveRedacted = redacted != null && !redacted.isEmpty(),
        haveSecondary = c.getSecondary() != null;
    if (haveRedacted || haveSecondary) {
      w.name("_meta").beginObject();
      if (haveRedacted) {
        w.name("redactedAttributes").beginArray();
        for (String a: redacted) {
          w.value(a);
        }
        w.endArray();
      }
      if (haveSecondary) {
        w.name("secondary").value(c.getSecondary());
      }
      w.endObject();
    }
    
    w.endObject();
  }
  
  private boolean isAttributeEntirelyPrivate(LDContext c, String attrName) {
    if (allAttributesPrivate) {
      return true;
    }
    AttributeRef privateRef = findPrivateRef(c, 1, attrName, null);
    return privateRef != null && privateRef.getDepth() == 1;
  }
  
  private List<String> writeOrRedactAttribute(
      JsonWriter w,
      LDContext c,
      String attrName,
      LDValue value,
      List<String> redacted
      ) throws IOException {
    if (allAttributesPrivate) {
      return addOrCreate(redacted, attrName);
    }
    return writeRedactedValue(w, c, 0, attrName, value, null, redacted);
  }
  
  // This method implements the context-aware attribute redaction logic, in which an attribute
  // can be 1. written as-is, 2. fully redacted, or 3. (for a JSON object) partially redacted.
  // It returns the updated redacted attribute list.
  private List<String> writeRedactedValue(
      JsonWriter w,
      LDContext c,
      int previousDepth,
      String attrName,
      LDValue value,
      AttributeRef previousMatchRef,
      List<String> redacted
      ) throws IOException {
    // See findPrivateRef for the meaning of the previousMatchRef parameter.
    int depth = previousDepth + 1;
    AttributeRef privateRef = findPrivateRef(c, depth, attrName, previousMatchRef);
    
    // If privateRef is non-null, then it is either an exact match for the property we're looking at,
    // or it refers to a subproperty of it (for instance, if we are redacting property "b" within
    // attriute "a", it could be /a/b [depth 2] or /a/b/c [depth 3]). If the depth shows that it's an
    // exact match, this whole value is redacted and we don't bother recursing.
    if (privateRef != null && privateRef.getDepth() == depth) {
      return addOrCreate(redacted, privateRef.toString());
    }
    
    // If privateRef is null (there was no matching private attribute)-- or, if privateRef isn't null
    // but it refers to a subproperty, and this value isn't an object so it has no properties-- then
    // we just write the value unredacted.
    if (privateRef == null || value.getType() != LDValueType.OBJECT) {
      writeNameAndValue(w, attrName, value);
      return redacted;
    }
    
    // At this point we know it is an object and we are redacting subproperties.
    w.name(attrName).beginObject();
    for (String name: value.keys()) {
      redacted = writeRedactedValue(w, c, depth, name, value.get(name), privateRef, redacted);
    }
    w.endObject();
    return redacted;
  }
  
  // Searches both the globally private attributes and the per-context private attributes to find a
  // match for the attribute or subproperty we're looking at.
  //
  // If we find one that exactly matches the current path (that is, the depth is the same), we
  // return that one, because that would tell us that the entire attribute/subproperty should be
  // redacted. If we don't find that, but we do find at least one match for a subproperty of this
  // path (that is, it has the current path as a prefix, but the depth is greater), then we return
  // it, to tell us that we'll need to recurse to redact subproperties.
  //
  // The previousMatchRef parameter is how we to keep track of the previous path segments we have
  // already matched when recursing. It starts out as null at the top level. Then, every time we
  // recurse to redact subproperties of an object, we set previousMatchRef to *any* AttributeRef
  // we've seen that has the current subpath as a prefix; such an AttributeRef is guaranteed to
  // exist, because we wouldn't have bothered to recurse if we hadn't found one, and we will only
  // be comparing components 0 through depth-1 of it (see matchPrivateRef), This shortcut allows
  // us to avoid allocating a variable-length mutable data structure such as a stack.
  private AttributeRef findPrivateRef(LDContext c, int depth, String attrName, AttributeRef previousMatchRef) {
    AttributeRef nonExactMatch = null;
    if (globalPrivateAttributes.length != 0) { // minor optimization to avoid creating an iterator if it's empty
      for (AttributeRef globalPrivate: globalPrivateAttributes) {
        if (matchPrivateRef(globalPrivate, depth, attrName, previousMatchRef)) {
          if (globalPrivate.getDepth() == depth) {
            return globalPrivate;
          }
          nonExactMatch = globalPrivate;
        }
      }
    }
    for (int i = 0; i < c.getPrivateAttributeCount(); i++) {
      AttributeRef contextPrivate = c.getPrivateAttribute(i);
      if (matchPrivateRef(contextPrivate, depth, attrName, previousMatchRef)) {
        if (contextPrivate.getDepth() == depth) {
          return contextPrivate;
        }
        nonExactMatch = contextPrivate;
      }
    }
    return nonExactMatch;
  }
  
  private static boolean matchPrivateRef(AttributeRef ref, int depth, String attrName, AttributeRef previousMatchRef) {
    if (ref.getDepth() < depth) {
      return false;
    }
    for (int i = 0; i < (depth - 1); i++) {
      if (!ref.getComponent(i).equals(previousMatchRef.getComponent(i))) {
        return false;
      }
    }
    return ref.getComponent(depth - 1).equals(attrName);
  }
  
  private static void writeNameAndValue(JsonWriter w, String name, LDValue value) throws IOException {
    w.name(name);
    gsonInstance().toJson(value, LDValue.class, w);
  }
  
  private static <T> List<T> addOrCreate(List<T> list, T value) {
    if (list == null) {
      list = new ArrayList<>();
    }
    list.add(value);
    return list;
  }
}
