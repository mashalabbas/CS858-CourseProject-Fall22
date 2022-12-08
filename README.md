# CS858-CourseProject-Fall22

The project is divided into two components, the main component is the WALA Analysis of the Framework to extract the features of APIs. 

To run the WALA analysis, the command "./gradlew startAnalysis -Dpath='./Input/framework' -Dtype=Framework" will be used. 

The Input directory should have the decompiled ROM you wish to Analyze. The java.jar and nonJava.jar files in the /lib should be changed according to the Android OS version 

The output of the program can be used to acheive Diff Analysis. Diff Analysis is used to detect the different APIs in two different ROMs.
