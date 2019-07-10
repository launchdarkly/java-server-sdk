#!/usr/bin/env bash

VERSION=$1

# Update version in gradle.properties file:
sed  -i.bak "s/^version.*$/version=${VERSION}/" gradle.properties
rm -f gradle.properties.bak

# Update version in README.md:
sed  -i.bak "s/<version>.*<\/version>/<version>${VERSION}<\/version>/" README.md
sed  -i.bak "s/\"com.launchdarkly:launchdarkly-java-server-sdk:.*\"/\"com.launchdarkly:launchdarkly-java-server-sdk:${VERSION}\"/" README.md
rm -f README.md.bak
