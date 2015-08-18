# Analyst Server

Analyst Server is a graphical user interface-driven, web-based tool for accessibility analysis of transportation networks:

<img src="splash.png" alt="Analyst Server performing accessibility analysis in Portland, Ore." />

## Installation

First, clone the repository locally. Analyst Server is built using [Maven](https://maven.apache.org/); it requires Java 8, and
runs under both Oracle Java and OpenJDK.

Next, build the application using

    mvn package

edit the configuration file `application.conf`, which is largely self-explanatory. We use [Stormpath](https://www.stormpath.com) to manage authentication, so you'll need to create an account there.

Next, start it by typing

    java -Xmx[several]G -jar target/analyst-server.jar

Finally, browse to [http://localhost:9090](http://localhost:9090) to log in and start the tutorial. You can specify a
port other than 9090 by specifying the port number in the configuration file. All of the data management tools will now work,
but you will need to set up computation in order to see any analysis results.

## Setting up computation

[TODO]

## Setting up custom logging

It is possible to log messages to Logentries. To do this, copy `logentries.xml.template` to any convenient
location, edit the file to add you logentries key, and start the server with `-Dlogback.configurationFile=path/to/logentries.xml`.
If it's in the working directory you must refer to it as `./logentries.xml` or Logback will attempt
to find it on the classpath.