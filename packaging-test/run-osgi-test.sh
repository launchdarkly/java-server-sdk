#!/bin/bash

echo ""
echo "  OSGi runtime test"
rm -rf ${TEMP_BUNDLE_DIR}
mkdir -p ${TEMP_BUNDLE_DIR}
cp $@ ${FELIX_BASE_BUNDLE_DIR}/* ${TEMP_BUNDLE_DIR}
rm -rf ${FELIX_DIR}/felix-cache
rm -f ${TEMP_OUTPUT}
touch ${TEMP_OUTPUT}

cd ${FELIX_DIR} && java -jar ${FELIX_JAR} -b ${TEMP_BUNDLE_DIR} | tee ${TEMP_OUTPUT}

grep "${SUCCESS_MESSAGE}" ${TEMP_OUTPUT} >/dev/null
