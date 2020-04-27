#!/bin/bash

function run_test() {
  rm -f ${TEMP_OUTPUT}
  touch ${TEMP_OUTPUT}
  classpath=$(echo "$@" | sed -e 's/ /:/g')
  java -classpath "$classpath" testapp.TestApp | tee ${TEMP_OUTPUT}
  grep "TestApp: PASS" ${TEMP_OUTPUT} >/dev/null  
}

echo ""
echo "  non-OSGi runtime test - with Gson"
run_test $@
grep "LDGson tests OK" ${TEMP_OUTPUT} >/dev/null || (echo "FAIL: should have run LDGson tests but did not" && exit 1)

# It does not make sense to test the "thin" jar without Gson. The SDK uses Gson internally
# and can't work without it; in the default jar and the "all" jar, it has its own embedded
# copy of Gson, but the "thin" jar does not include any third-party dependencies so you must
# provide all of them including Gson.
thin_sdk_regex=".*launchdarkly-java-server-sdk-[^ ]*-thin\\.jar"
if [[ "$@" =~ $thin_sdk_regex ]]; then
  exit 0
fi

echo ""
echo "  non-OSGi runtime test - without Gson"
deps_except_json=""
json_jar_regex=".*gson.*"
for dep in $@; do
  if [[ ! "$dep" =~ $json_jar_regex ]]; then
    deps_except_json="$deps_except_json $dep"
  fi
done
run_test $deps_except_json
grep "skipping LDGson tests" ${TEMP_OUTPUT} >/dev/null || \
  (echo "FAIL: should have skipped LDGson tests but did not; test setup was incorrect" && exit 1)
