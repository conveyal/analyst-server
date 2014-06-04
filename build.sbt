name := "analyst-server"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "org.mapdb" % "mapdb" % "1.0.1",
  "org.julienrf" %% "play-jsmessages" % "1.6.1"
)     

play.Project.playJavaSettings
