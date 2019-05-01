#!/usr/bin/env bash
# This script updates the version for the java-server-sdk library and releases the artifact + javadoc
# It will only work if you have the proper credentials set up in ~/.gradle/gradle.properties

# It takes exactly one argument: the new version.
# It should be run from the root of this git repo like this:
#   ./scripts/release.sh 4.0.9

# When done you should commit and push the changes made.

set -uxe
echo "Starting java-server-sdk release."

$(dirname $0)/update-version.sh $1

./gradlew clean publish closeAndReleaseRepository
./gradlew publishGhPages
echo "Finished java-server-sdk release."
