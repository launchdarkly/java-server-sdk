package com.launchdarkly.client.files;

import com.google.gson.JsonElement;
import com.launchdarkly.client.DataModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implements the loading of flag data from one or more files. Will throw an exception if any file can't
 * be read or parsed, or if any flag or segment keys are duplicates.
 */
final class DataLoader {
  private final List<Path> files;

  public DataLoader(List<Path> files) {
    this.files = new ArrayList<Path>(files);
  }
  
  public Iterable<Path> getFiles() {
    return files;
  }
  
  public void load(DataBuilder builder) throws DataLoaderException
  {
    for (Path p: files) {
      try {
        byte[] data = Files.readAllBytes(p);
        FlagFileParser parser = FlagFileParser.selectForContent(data);
        FlagFileRep fileContents = parser.parse(new ByteArrayInputStream(data));
        if (fileContents.flags != null) {
          for (Map.Entry<String, JsonElement> e: fileContents.flags.entrySet()) {
            builder.add(DataModel.DataKinds.FEATURES, FlagFactory.flagFromJson(e.getValue()));
          }
        }
        if (fileContents.flagValues != null) {
          for (Map.Entry<String, JsonElement> e: fileContents.flagValues.entrySet()) {
            builder.add(DataModel.DataKinds.FEATURES, FlagFactory.flagWithValue(e.getKey(), e.getValue()));
          }
        }
        if (fileContents.segments != null) {
          for (Map.Entry<String, JsonElement> e: fileContents.segments.entrySet()) {
            builder.add(DataModel.DataKinds.SEGMENTS, FlagFactory.segmentFromJson(e.getValue()));
          }
        }
      } catch (DataLoaderException e) {
        throw new DataLoaderException(e.getMessage(), e.getCause(), p);
      } catch (IOException e) {
        throw new DataLoaderException(null, e, p);
      }
    }
  }
}
