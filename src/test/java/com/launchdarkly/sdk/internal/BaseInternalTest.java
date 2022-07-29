package com.launchdarkly.sdk.internal;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogCapture;
import com.launchdarkly.logging.Logs;

import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

@SuppressWarnings("javadoc")
public class BaseInternalTest {
  @Rule public DumpLogIfTestFails dumpLogIfTestFails;
  
  protected final LDLogAdapter testLogging;
  protected final LDLogger testLogger;
  protected final LogCapture logCapture;
  
  protected BaseInternalTest() {
    logCapture = Logs.capture();
    testLogging = logCapture;
    testLogger = LDLogger.withAdapter(testLogging, "");
    dumpLogIfTestFails = new DumpLogIfTestFails();
  }
  
  class DumpLogIfTestFails extends TestWatcher {
    @Override
    protected void failed(Throwable e, Description description) {
      for (LogCapture.Message message: logCapture.getMessages()) {
        System.out.println("LOG {" + description.getDisplayName() + "} >>> " + message.toStringWithTimestamp());
      }
    }
  }
}
