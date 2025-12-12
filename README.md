# CV-Segmentation-FIJI: Chan-Vese Segmentation Plugin for ImageJ/FIJI

This repository contains the source code for an ImageJ/FIJI plugin implementing the Chan-Vese (CV) Active Contours Without Edges segmentation model.

## Installation and Usage Guide

This guide assumes you are using FIJI (ImageJ).

### Option 1: Quick Install

1. Download CV-Segmentation-FIJI.jar from the releases/
2. Copy the downloaded CV-Segmentation-FIJI.jar file into the FIJI plugins folder (e.g., ~/Fiji/plugins/)
3. Restart FIJI
4. Run the Plugin: Plugins > RBNB > CV Segmentation

## Option 2: Building from Source

1. This project uses [Maven](https://maven.apache.org/download.cgi) for dependency management.
2. Clone the Repository:
    ```commandline
    git clone https://github.com/hmynssen/CV-Segmentation-FIJI.git
    cd CV-Segmentation-FIJI
    ```
3. Build the Project: Use Maven to compile the code and package it into a JAR file:
    ````commandline
    mvn clean package
    ````
