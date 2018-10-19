package com.launchdarkly.client.files;

import java.nio.file.Path;

/**
 * Indicates that the file processor encountered an error in one of the input files. This exception is
 * not surfaced to the host application, it is only logged, and we don't do anything different programmatically
 * with different kinds of exceptions, therefore it has no subclasses.
 */
@SuppressWarnings("serial")
class DataLoaderException extends Exception {
  private final Path filePath;
  
  public DataLoaderException(String message, Throwable cause, Path filePath) {
    super(message, cause);
    this.filePath = filePath;
  }

  public DataLoaderException(String message, Throwable cause) {
    this(message, cause, null);
  }

  public Path getFilePath() {
    return filePath;
  }
  
  public String getDescription() {
    StringBuilder s = new StringBuilder();
    if (getMessage() != null) {
      s.append(getMessage());
      if (getCause() != null) {
        s.append(" ");
      }
    }
    if (getCause() != null) {
      s.append(" [").append(getCause().toString()).append("]");
    }
    if (filePath != null) {
      s.append(": ").append(filePath);
    }
    return s.toString();
  }
}
