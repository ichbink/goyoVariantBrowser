logLevel := Level.Debug
//resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers +=  Resolver.typesafeRepo("releases")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.8")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.12")
addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.0.1")
addSbtPlugin("com.cavorite" % "sbt-avro" % "0.3.2")
//addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")