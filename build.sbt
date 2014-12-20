name := "analyst-server"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "org.mapdb" % "mapdb" % "1.0.1",
  "org.julienrf" %% "play-jsmessages" % "1.6.2",
  "commons-io" % "commons-io" % "2.4",
  "com.amazonaws" % "aws-java-sdk" % "1.7.13",
  "com.typesafe.akka" % "akka-remote_2.10" % "2.3.5"
)

watchSources := (watchSources.value
  --- baseDirectory.value / "app/assets" ** "*"
  --- baseDirectory.value / "conf" ** "*"
  --- baseDirectory.value / "public" ** "*").get
  
