# Analyst Server

Analyst Server is a graphical user interface-driven, web-based tool for accessibility analysis of transportation networks:

<img src="splash.png" alt="Analyst Server performing accessibility analysis in Portland, Ore." />

## Installation

First, clone the repository locally. Analyst Server is built using [Maven](https://maven.apache.org/); it requires Java 8, and runs under both Oracle Java and OpenJDK. Next, build the application using `mvn clean package`.

Copy the configuration file `application.conf.template` to `application.conf` and edit it to reflect your configuration. Comments in the configuration file explain the purpose of each line. Transport Analyst uses [Stormpath](https://www.stormpath.com) to manage authentication, so you'll need to create an account there and supply your Stormpath API key file and application ID in `application.conf`.

If you'd rather not use Stormpath, you can disable authentication entirely by adding the line `auth.enabled=false` in your `application.conf`.

## AWS credentials

Transport Analyst stores GTFS/OSM data and analysis results on Amazon S3 to allow persistence and easy transfer between worker and UI components. There are several ways to make your AWS IAM credentials known to Transport Analyst server, broker, and workers. Two common ways are the environment variables AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY or a Java properties file containing credentials at `~/.aws/credentials`. Both methods are described on the page  http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html.

## Running

Next, start the Transport Analyst server by typing

    java -Xmx[several]G -jar target/analyst-server.jar

Finally, browse to [http://localhost:9090](http://localhost:9090) to log in and start the tutorial. You can specify a
port other than 9090 by specifying the port number in the configuration file. All of the data management tools will now work, but you will need to run a message broker and worker instances in order to see any analysis results.

## Setting up computation

The Analyst server distributes tasks to a cluster of worker instances, and work is coordinated by a broker component. The broker automatically starts up worker instances as needed on AWS EC2, and workers will shut down the machine they are running on after a period of inactivity. The broker and workers are started up as separate processes, usually on separate virtual machines.

The broker and worker code is entirely within the OpenTripPlanner repository, but because analyst-server includes OpenTripPlanner as a Maven dependency, all the necessary runnable main classes are present in a build of analyst-server. However, when doing development work it's best to run the worker and broker processes from an OTP project in your IDE. This way you can change the worker code on the fly without deploying an OTP Maven artifact and rebuilding the Transport Analyst server.

For debugging purposes we can tell the broker not to start up any EC2 instances, then run one worker process manually.

The broker main class is `org.opentripplanner.analyst.broker.BrokerMain`. The worker main class is `org.opentripplanner.analyst.cluster.AnalystWorker`.

[TODO] Describe running broker and worker instances and their config files, including offline debug mode.

## Setting up custom logging

It is possible to log messages to Logentries. To do this, copy `logentries.xml.template` to any convenient
location, edit the file to add you logentries key, and start the server with `-Dlogback.configurationFile=path/to/logentries.xml`.
If it's in the working directory you must refer to it as `./logentries.xml` or Logback will attempt
to find it on the classpath.

## Internationalization

In order to add a new interface language to Transport Analyst, first duplicate one of the existing language files in `src/main/resources/messages`, changing the file name suffix to the new language's two-letter code. After translating all the strings in the file, edit `src/main/resources/public/templates/app/app-nav.html`. Duplicate one of the selectLang list entries, changing the data-lang code to match the two-letter code of your new translation file and filling in the localized name of the language (the autoglottonym).
