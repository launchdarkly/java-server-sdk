package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Segment;
import com.launchdarkly.sdk.server.DataStoreTestTypes.DataBuilder;
import com.launchdarkly.sdk.server.StreamProcessorEvents.DeleteData;
import com.launchdarkly.sdk.server.StreamProcessorEvents.PatchData;
import com.launchdarkly.sdk.server.StreamProcessorEvents.PutData;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.FullDataSet;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes.ItemDescriptor;
import com.launchdarkly.sdk.server.subsystems.SerializationException;

import org.junit.Test;

import static com.launchdarkly.sdk.server.DataModel.FEATURES;
import static com.launchdarkly.sdk.server.DataModel.SEGMENTS;
import static com.launchdarkly.sdk.server.JsonHelpers.serialize;
import static com.launchdarkly.sdk.server.ModelBuilders.flagBuilder;
import static com.launchdarkly.sdk.server.ModelBuilders.segmentBuilder;
import static com.launchdarkly.sdk.server.StreamProcessorEvents.parseDeleteData;
import static com.launchdarkly.sdk.server.StreamProcessorEvents.parsePatchData;
import static com.launchdarkly.sdk.server.StreamProcessorEvents.parsePutData;
import static com.launchdarkly.sdk.server.TestUtil.assertDataSetEquals;
import static com.launchdarkly.sdk.server.TestUtil.assertItemEquals;
import static com.launchdarkly.sdk.server.TestUtil.assertThrows;
import static com.launchdarkly.sdk.server.TestUtil.jsonReaderFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class StreamProcessorEventsTest {
  
  @Test
  public void parsingPutData() throws Exception {
    FeatureFlag flag = flagBuilder("flag1").version(1000).build();
    Segment segment = segmentBuilder("segment1").version(1000).build();

    String allDataJson = "{" +
      "\"flags\": {\"flag1\":" + serialize(flag) + "}" +
      ",\"segments\": {\"segment1\":" + serialize(segment) + "}}";
    FullDataSet<ItemDescriptor> expectedAllData = DataBuilder.forStandardTypes()
        .addAny(FEATURES, flag).addAny(SEGMENTS, segment).build();
    String validInput = "{\"path\": \"/\", \"data\":" + allDataJson + "}";
    PutData validResult = parsePutData(jsonReaderFrom(validInput));
    assertThat(validResult.path, equalTo("/"));
    assertDataSetEquals(expectedAllData, validResult.data);
    
    String inputWithoutPath = "{\"data\":" + allDataJson + "}";
    PutData resultWithoutPath = parsePutData(jsonReaderFrom(inputWithoutPath));
    assertThat(resultWithoutPath.path, nullValue());
    assertDataSetEquals(expectedAllData, validResult.data);
    
    String inputWithoutData = "{\"path\":\"/\"}";
    assertThrows(SerializationException.class,
      () -> parsePutData(jsonReaderFrom(inputWithoutData)));
  }

  @Test
  public void parsingPatchData() throws Exception {
    FeatureFlag flag = flagBuilder("flag1").version(1000).build();
    Segment segment = segmentBuilder("segment1").version(1000).build();
    String flagJson = serialize(flag), segmentJson = serialize(segment);

    String validFlagInput = "{\"path\":\"/flags/flag1\", \"data\":" + flagJson + "}";
    PatchData validFlagResult = parsePatchData(jsonReaderFrom(validFlagInput));
    assertThat(validFlagResult.kind, equalTo(FEATURES));
    assertThat(validFlagResult.key, equalTo(flag.getKey()));
    assertItemEquals(flag, validFlagResult.item);
    
    String validSegmentInput = "{\"path\":\"/segments/segment1\", \"data\":" + segmentJson + "}";
    PatchData validSegmentResult = parsePatchData(jsonReaderFrom(validSegmentInput));
    assertThat(validSegmentResult.kind, equalTo(SEGMENTS));
    assertThat(validSegmentResult.key, equalTo(segment.getKey()));
    assertItemEquals(segment, validSegmentResult.item);

    String validFlagInputWithDataBeforePath = "{\"data\":" + flagJson + ",\"path\":\"/flags/flag1\"}";
    PatchData validFlagResultWithDataBeforePath = parsePatchData(
        jsonReaderFrom(validFlagInputWithDataBeforePath));
    assertThat(validFlagResultWithDataBeforePath.kind, equalTo(FEATURES));
    assertThat(validFlagResultWithDataBeforePath.key, equalTo(flag.getKey()));
    assertItemEquals(flag, validFlagResultWithDataBeforePath.item);
    
    String validSegmentInputWithDataBeforePath = "{\"data\":" + segmentJson + ",\"path\":\"/segments/segment1\"}";
    PatchData validSegmentResultWithDataBeforePath = parsePatchData(
        jsonReaderFrom(validSegmentInputWithDataBeforePath));
    assertThat(validSegmentResultWithDataBeforePath.kind, equalTo(SEGMENTS));
    assertThat(validSegmentResultWithDataBeforePath.key, equalTo(segment.getKey()));
    assertItemEquals(segment, validSegmentResultWithDataBeforePath.item);

    String inputWithUnrecognizedPath = "{\"path\":\"/cats/lucy\", \"data\":" + flagJson + "}";
    PatchData resultWithUnrecognizedPath = parsePatchData(
        jsonReaderFrom(inputWithUnrecognizedPath));
    assertThat(resultWithUnrecognizedPath.kind, nullValue());
    
    String inputWithMissingPath = "{\"data\":" + flagJson + "}";
    assertThrows(SerializationException.class,
        () -> parsePatchData(jsonReaderFrom(inputWithMissingPath)));
    
    String inputWithMissingData = "{\"path\":\"/flags/flag1\"}";
    assertThrows(SerializationException.class,
        () -> parsePatchData(jsonReaderFrom(inputWithMissingData)));
  }
  
  @Test
  public void parsingDeleteData() {
    String validFlagInput = "{\"path\":\"/flags/flag1\", \"version\": 3}";
    DeleteData validFlagResult = parseDeleteData(jsonReaderFrom(validFlagInput));
    assertThat(validFlagResult.kind, equalTo(FEATURES));
    assertThat(validFlagResult.key, equalTo("flag1"));
    assertThat(validFlagResult.version, equalTo(3));

    String validSegmentInput = "{\"path\":\"/segments/segment1\", \"version\": 4}";
    DeleteData validSegmentResult = parseDeleteData(jsonReaderFrom(validSegmentInput));
    assertThat(validSegmentResult.kind, equalTo(SEGMENTS));
    assertThat(validSegmentResult.key, equalTo("segment1"));
    assertThat(validSegmentResult.version, equalTo(4));

    String inputWithUnrecognizedPath = "{\"path\":\"/cats/macavity\", \"version\": 9}";
    DeleteData resultWithUnrecognizedPath = parseDeleteData(jsonReaderFrom(inputWithUnrecognizedPath));
    assertThat(resultWithUnrecognizedPath.kind, nullValue());
    
    String inputWithMissingPath = "{\"version\": 1}";
    assertThrows(SerializationException.class,
        () -> parseDeleteData(jsonReaderFrom(inputWithMissingPath)));

    String inputWithMissingVersion = "{\"path\": \"/flags/flag1\"}";
    assertThrows(SerializationException.class,
        () -> parseDeleteData(jsonReaderFrom(inputWithMissingVersion)));    
  }
}
