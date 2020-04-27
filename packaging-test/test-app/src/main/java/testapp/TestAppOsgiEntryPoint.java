package testapp;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class TestAppOsgiEntryPoint implements BundleActivator {
  public void start(BundleContext context) throws Exception {
    System.out.println("TestApp: starting test bundle");

    TestApp.main(new String[0]);

    System.exit(0);
  }

  public void stop(BundleContext context) throws Exception {
  }
}