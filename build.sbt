name := "actorcluster"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.0"

libraryDependencies += "com.typesafe.akka" %% "akka-cluster-experimental" % "2.1.0"

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.1.0" % "runtime"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime"
