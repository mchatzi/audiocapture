# Audio capture and SPL viewer
This program will open Macbook's "Built-in Microphone" and start listening and rendering the waveform when you press "Capture". 

Code is just baked

https://mchatzi.github.io/audiocapture/

## Build and run
Easier way to run this is in Intellij, open the project folder as a java project, add any dependencies needed and create a new Run configuration, "Application"

## Todos
* Verify that the processing of multi bytes (for 16 and 24 bit signals) is correct
* Verify decibel display mode
* Add 2-channel support
* Any suggestions for dependency management and easier setup (and alternatives to maven) welcome!
* Find an easy way to build an all-inclusive jar (with or without maven shade plugin) so we can run this with "java -jar"
    * maven shade plugin
    * maven assemply plugin
    * maven oneJar plugin
    * eclipse ide built-in tool
    * fatJar
    * fatJar via gradle
    * gradle application plugin
