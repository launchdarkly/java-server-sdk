package com.launchdarkly.sdk.server;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.logging.LogCapture;
import com.launchdarkly.logging.Logs;

import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

@SuppressWarnings("javadoc")
public class BaseTest {
  @Rule public DumpLogIfTestFails dumpLogIfTestFails;
  
  protected final LDLogAdapter testLogging;
  protected final LDLogger testLogger;
  protected final LogCapture logCapture;
  
  protected BaseTest() {
    logCapture = Logs.capture();
    testLogging = logCapture;
    testLogger = LDLogger.withAdapter(testLogging, "");
    dumpLogIfTestFails = new DumpLogIfTestFails();
  }
  
  /**
   * Creates a configuration builder with the basic properties that we want for all tests unless
   * otherwise specified: do not connect to an external data source, do not send events, and
   * redirect all logging to the test logger for the current test (which will be printed to the
   * console only if the test fails).
   * 
   * @return a configuraiton builder
   */
  protected LDConfig.Builder baseConfig() {
    return new LDConfig.Builder()
        .dataSource(Components.externalUpdatesOnly())
        .events(Components.noEvents())
        .logging(Components.logging(testLogging).level(LDLogLevel.DEBUG));
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
