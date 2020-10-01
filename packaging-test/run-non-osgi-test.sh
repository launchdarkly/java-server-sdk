#!/bin/bash

set -e

function run_test() {
  rm -f ${TEMP_OUTPUT}
  touch ${TEMP_OUTPUT}
  classpath=$(echo "$@" | sed -e 's/ /:/g')
  java -classpath "$classpath" testapp.TestApp | tee ${TEMP_OUTPUT}
  grep "TestApp: PASS" ${TEMP_OUTPUT} >/dev/null  
}

# It does not make sense to test the "thin" jar without Gson. The SDK uses Gson internally
# and can't work without it; in the default jar and the "all" jar, it has its own embedded
# copy of Gson, but the "thin" jar does not include any third-party dependencies so you must
# provide all of them including Gson.
echo ""
if [[ "$@" =~ $thin_sdk_regex ]]; then
  echo "  non-OSGi runtime test - without Jackson"
  filtered_deps=""
  json_jar_regex=".*jackson.*"
  for dep in $@; do
    if [[ ! "$dep" =~ $json_jar_regex ]]; then
      filtered_deps="$filtered_deps $dep"
    fi
  done
  run_test $filtered_deps
  grep "skipping LDJackson tests" ${TEMP_OUTPUT} >/dev/null || \
    (echo "FAIL: should have skipped LDJackson tests but did not; test setup was incorrect" && exit 1)
else
  echo "  non-OSGi runtime test - without Gson or Jackson"
  filtered_deps=""
  json_jar_regex=".*gson.*|.*jackson.*"
  for dep in $@; do
    if [[ ! "$dep" =~ $json_jar_regex ]]; then
      filtered_deps="$filtered_deps $dep"
    fi
  done
  run_test $filtered_deps
  grep "skipping LDGson tests" ${TEMP_OUTPUT} >/dev/null || \
    (echo "FAIL: should have skipped LDGson tests but did not; test setup was incorrect" && exit 1)
  grep "skipping LDJackson tests" ${TEMP_OUTPUT} >/dev/null || \
    (echo "FAIL: should have skipped LDJackson tests but did not; test setup was incorrect" && exit 1)
fi

echo ""
echo "  non-OSGi runtime test - with Gson and Jackson"
run_test $@
grep "LDGson tests OK" ${TEMP_OUTPUT} >/dev/null || (echo "FAIL: should have run LDGson tests but did not" && exit 1)
grep "LDJackson tests OK" ${TEMP_OUTPUT} >/dev/null || (echo "FAIL: should have run LDJackson tests but did not" && exit 1)
