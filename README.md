# Audio capture and SPL viewer
This program will open Macbook's "Built-in Microphone" and start listening and rendering the waveform when you press "Capture". 
Code is just baked

## Build and run
Easier way to run this is in Intellij, open the project folder as a java project, add any dependencies needed and create a new Run configuration, "Application"

## Todos

* Verify that the processing of multi bytes (for 16 and 24 bit signals) is correct
* Investigate why the sign of each byte, in the multi-byte case again, may be different 
* Convert display from linear to logarithmic (show decibels; at the moment the value of each samples is printed as is)
* Any suggestions for dependency management and easier setup (and alternatives to maven) welcome!
* Find an easy way to build an all-inclusive jar (with or without maven shade plugin) so we can run this with "java -jar"