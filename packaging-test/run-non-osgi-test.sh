#!/bin/bash

echo ""
echo "  non-OSGi runtime test"
java -classpath $(echo "$@" | sed -e 's/ /:/g') testapp.TestApp | tee ${TEMP_OUTPUT}
grep "${SUCCESS_MESSAGE}" ${TEMP_OUTPUT} >/dev/null
