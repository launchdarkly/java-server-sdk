package com.launchdarkly.sdk.server;

import com.google.gson.annotations.JsonAdapter;
import com.launchdarkly.sdk.server.subsystems.SerializationException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("javadoc")
public class JsonHelpersTest {
  @Test
  public void serialize() {
    MySerializableClass instance = new MySerializableClass();
    instance.value = 3;
    assertEquals("{\"value\":3}", JsonHelpers.serialize(instance));
  }

  @Test
  public void deserialize() {
    MySerializableClass instance = JsonHelpers.deserialize("{\"value\":3}", MySerializableClass.class);
    assertNotNull(instance);
    assertEquals(3, instance.value);
  }
  
  @Test(expected=SerializationException.class)
  public void deserializeInvalidJson() {
    JsonHelpers.deserialize("{\"value", MySerializableClass.class);
  }
  
  @Test
  public void postProcessingTypeAdapterFactoryCallsAfterDeserializedIfApplicable() {
    // This tests the mechanism that ensures afterDeserialize() is called on every FeatureFlag or
    // Segment that we deserialize.
    MyClassWithAnAfterDeserializeMethod instance =
        JsonHelpers.gsonInstance().fromJson("{}", MyClassWithAnAfterDeserializeMethod.class);
    assertNotNull(instance);
    assertTrue(instance.wasCalled);
  }

  @Test
  public void postProcessingTypeAdapterFactoryDoesNothingIfClassDoesNotImplementInterface() {
    // If we accidentally apply this type adapter to something inapplicable, it's a no-op.
    SomeOtherClass instance = JsonHelpers.gsonInstance().fromJson("{}", SomeOtherClass.class);
    assertNotNull(instance);
  }

  @Test
  public void postProcessingTypeAdapterFactoryDoesNotAffectSerialization() {
    MyClassWithAnAfterDeserializeMethod instance = new MyClassWithAnAfterDeserializeMethod();
    String json = JsonHelpers.gsonInstance().toJson(instance);
    assertEquals("{\"wasCalled\":false}", json);
  }

  static class MySerializableClass {
    int value;
  }
  
  @JsonAdapter(JsonHelpers.PostProcessingDeserializableTypeAdapterFactory.class)
  static class MyClassWithAnAfterDeserializeMethod implements JsonHelpers.PostProcessingDeserializable {
    boolean wasCalled = false;

    @Override
    public void afterDeserialized() {
      wasCalled = true;
    }
  }

  @JsonAdapter(JsonHelpers.PostProcessingDeserializableTypeAdapterFactory.class)
  static class SomeOtherClass {}
}
