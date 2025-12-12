#!/bin/bash

echo "Running Maven Build..."
mvn -U clean package || { echo "Maven build failed"; exit 1; }

PROJECT_ARTIFACT="cv-segmentation-0.1.0-SNAPSHOT.jar"
TARGET_PATH="target/${PROJECT_ARTIFACT}"
FIJI_PLUGINS_DIR="$HOME/Fiji/plugins"
FIJI_APP_PATH="$HOME/Fiji/fiji"
DEST_JAR_NAME="CV_Segmentation.jar"

echo "Copying JAR to Fiji plugins..."
if [ -f "$TARGET_PATH" ]; then
    cp "$TARGET_PATH" "$FIJI_PLUGINS_DIR/$DEST_JAR_NAME"
    echo "Copied: $TARGET_PATH -> $FIJI_PLUGINS_DIR/$DEST_JAR_NAME"
else
    echo "Error: Compiled JAR not found at $TARGET_PATH"
    exit 1
fi


echo "Launching Fiji..."
"$FIJI_APP_PATH" &