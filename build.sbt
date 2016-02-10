name := """sikuliator-client"""

version := "0.0.1-SNAPHSOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"



libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.1",
  "com.ning" % "async-http-client" % "1.9.33",
//  "com.sikulix" % "sikulixapi" % "1.1.0", //has profiles which are not supported by sbt
  "com.sikulix" % "sikulixlibswin" % "1.1.0",
  "commons-cli" % "commons-cli" % "1.2",
  "org.apache.commons" % "commons-exec" % "1.3",
  "com.melloware" % "jintellitype" % "1.3.7",
  "jxgrabkey" % "jxgrabkey" % "1.0",
  "org.swinglabs" % "swing-layout" % "1.0.3",
  "com.nativelibs4java" % "bridj" % "0.6.2"
)
//resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += "unidue" at "http://mvn.is.inf.uni-due.de:8081/nexus/content/repositories/atunes-dependencies/"
// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

//fork in run := true
