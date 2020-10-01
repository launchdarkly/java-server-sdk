#!/bin/bash

set -e

# This script uses Felix to run the test application as an OSGi bundle, with or without
# additional bundles to support the optional Gson and Jackson integrations. We are
# verifying that the SDK itself works correctly as an OSGi bundle, and also that its
# imports of other bundles work correctly.
#
# This test is being run in CI using the lowest compatible JDK version. It may not work
# in higher JDK versions due to incompatibilities with the version of Felix we are using.

JAR_DEPS="$@"

# We can't test the "thin" jar in OSGi, because some of our third-party dependencies
# aren't available as OSGi bundles. That isn't a plausible use case anyway.
thin_sdk_regex=".*launchdarkly-java-server-sdk-[^ ]*-thin\\.jar"
if [[ "${JAR_DEPS}" =~ $thin_sdk_regex ]]; then
  exit 0
fi

rm -rf ${TEMP_BUNDLE_DIR}
mkdir -p ${TEMP_BUNDLE_DIR}

function copy_deps() {
  if [ -n "${JAR_DEPS}" ]; then
    cp ${JAR_DEPS} ${TEMP_BUNDLE_DIR}
  fi
  cp ${FELIX_BASE_BUNDLE_DIR}/* ${TEMP_BUNDLE_DIR}
}

function run_test() {
  rm -rf ${FELIX_DIR}/felix-cache
  rm -f ${TEMP_OUTPUT}
  touch ${TEMP_OUTPUT}
  cd ${FELIX_DIR}
  java -jar ${FELIX_JAR} -b ${TEMP_BUNDLE_DIR} | tee ${TEMP_OUTPUT}
  grep "TestApp: PASS" ${TEMP_OUTPUT} >/dev/null
}

echo ""
echo "  OSGi runtime test - without Gson or Jackson"
copy_deps
rm ${TEMP_BUNDLE_DIR}/*gson*.jar ${TEMP_BUNDLE_DIR}/*jackson*.jar
ls ${TEMP_BUNDLE_DIR}
run_test
grep "skipping LDGson tests" ${TEMP_OUTPUT} >/dev/null || \
  (echo "FAIL: should have skipped LDGson tests but did not; test setup was incorrect" && exit 1)
grep "skipping LDJackson tests" ${TEMP_OUTPUT} >/dev/null || \
  (echo "FAIL: should have skipped LDJackson tests but did not; test setup was incorrect" && exit 1)

echo ""
echo "  OSGi runtime test - with Gson and Jackson"
copy_deps
run_test
grep "LDGson tests OK" ${TEMP_OUTPUT} >/dev/null || (echo "FAIL: should have run LDGson tests but did not" && exit 1)
grep "LDJackson tests OK" ${TEMP_OUTPUT} >/dev/null || (echo "FAIL: should have run LDJackson tests but did not" && exit 1)
