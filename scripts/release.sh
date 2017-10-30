#!/usr/bin/env bash
# This script updates the version for the java-client library and releases the artifact + javadoc
# It will only work if you have the proper credentials set up in ~/.gradle/gradle.properties

# It takes exactly one argument: the new version.
# It should be run from the root of this git repo like this:
#   ./scripts/release.sh 4.0.9

# When done you should commit and push the changes made.

set -uxe
echo "Starting java-client release."

VERSION=$1

# Update version in gradle.properties file:
sed  -i.bak "s/^version.*$/version=${VERSION}/" gradle.properties
rm -f gradle.properties.bak

# Update version in README.md:
sed  -i.bak "s/<version>.*<\/version>/<version>${VERSION}<\/version>/" README.md
rm -f README.md.bak

./gradlew clean test install sourcesJar javadocJar uploadArchives closeAndReleaseRepository
./gradlew publishGhPages
echo "Finished java-client release."
