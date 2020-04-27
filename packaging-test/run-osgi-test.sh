#!/bin/bash

# We can't test the "thin" jar in OSGi, because some of our third-party dependencies
# aren't available as OSGi bundles. That isn't a plausible use case anyway.
thin_sdk_regex=".*launchdarkly-java-server-sdk-[^ ]*-thin\\.jar"
if [[ "$@" =~ $thin_sdk_regex ]]; then
  exit 0
fi

rm -rf ${TEMP_BUNDLE_DIR}
mkdir -p ${TEMP_BUNDLE_DIR}

function run_test() {
  rm -rf ${FELIX_DIR}/felix-cache
  rm -f ${TEMP_OUTPUT}
  touch ${TEMP_OUTPUT}
  cd ${FELIX_DIR} && java -jar ${FELIX_JAR} -b ${TEMP_BUNDLE_DIR} | tee ${TEMP_OUTPUT}
  grep "TestApp: PASS" ${TEMP_OUTPUT} >/dev/null
}

echo ""
echo "  OSGi runtime test - with Gson"
cp $@ ${FELIX_BASE_BUNDLE_DIR}/* ${TEMP_BUNDLE_DIR}
run_test
grep "LDGson tests OK" ${TEMP_OUTPUT} >/dev/null || (echo "FAIL: should have run LDGson tests but did not" && exit 1)

echo ""
echo "  OSGi runtime test - without Gson"
rm ${TEMP_BUNDLE_DIR}/*gson*.jar
run_test
grep "skipping LDGson tests" ${TEMP_OUTPUT} >/dev/null || \
  (echo "FAIL: should have skipped LDGson tests but did not; test setup was incorrect" && exit 1)
