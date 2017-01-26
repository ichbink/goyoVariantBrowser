import sbt.plugins

autoScalaLibrary := false

lazy val scalaV = "2.11.7"

//(version in avroConfig ) := "1.8.0"

libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka_2.11" % "0.10.0.1"
    exclude("javax.jms", "jms")
    exclude("com.sun.jdmk", "jmxtools")
    exclude("com.sun.jmx", "jmxri")
    exclude("org.slf4j", "slf4j-log4j12")
  ,"org.apache.kafka" % "kafka-clients" % "0.10.0.1" exclude("org.slf4j", "slf4j-log4j12")
  ,"org.apache.kafka" % "kafka-streams" % "0.10.0.1" exclude("org.slf4j", "slf4j-log4j12")
 // ,"com.typesafe.akka" %% "akka-stream-kafka" % "0.12"
  ,"me.chrons" %% "boopickle" % "1.2.4"
  ,"com.sksamuel.avro4s" %% "avro4s-core" % "1.6.2"
//  ,"org.apache.avro" % "avro-compiler" % "1.7.5"
  ,"io.confluent" % "kafka-avro-serializer" % "3.0.1" exclude("org.slf4j", "slf4j-log4j12")
  //,"org.apache.kafka" % "kafka-log4j-appender" % "0.10.0.1"
  //,"com.typesafe" % "config" % "1.3.1"
)

resolvers += "confluent" at "https://packages.confluent.io/maven/"
