import sbt.Project.projectToRef
import sbt._

name := "goyoVariantBrowser"

version := "1.0"

autoScalaLibrary := false

lazy val clients = Seq(variantBrowser)
lazy val scalaV = "2.11.7"

lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared")).
  settings(
    scalaVersion := scalaV,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.5.4"
      ,"org.scala-js" %%% "scalajs-dom" % "0.8.2"
      ,"be.doeraene" %%% "scalajs-jquery" % "0.9.0"
      ,"com.github.japgolly.scalajs-react" %%% "core" % "0.11.1"
      ,"com.lihaoyi" %%% "autowire" % "0.2.5"
      ,"me.chrons" %% "boopickle" % "1.2.4"
    )
  )
  .enablePlugins(ScalaJSPlugin, SbtWeb)
  .jsConfigure(_ enablePlugins SbtWeb)


lazy val sharedJvm = shared.jvm.settings(name := "sharedJVM")
lazy val sharedJs = shared.js.settings(name := "sharedJS")

lazy val `goyovariantbrowser` = (project in file("jvm")).settings (
  scalaJSProjects := clients,
  scalaVersion := scalaV,
  pipelineStages in Assets := Seq(scalaJSPipeline),
  libraryDependencies ++= Seq(
    "com.vmunier" %% "scalajs-scripts" % "1.0.0"
    ,"me.chrons" %% "boopickle" % "1.2.4"
    ,"org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided"
    ,"org.webjars" %% "webjars-play" % "2.5.0"
    ,"com.typesafe" % "config" % "1.2.1",
    "org.webjars" % "bootstrap" % "3.3.6"
  ),
  routesGenerator := InjectedRoutesGenerator
).enablePlugins(PlayScala)
    .aggregate( kafkaConnector)
    .aggregate(clients.map(projectToRef): _*)
    .dependsOn(sharedJvm, kafkaConnector)

lazy val kafkaConnector = (project in file ("kafkaConnector")).settings(
  scalaVersion := scalaV
).dependsOn(sharedJvm)


lazy val variantBrowser = ( project in file("js")).settings(
  persistLauncher := true,
  persistLauncher in Test := false,
  scalaVersion := scalaV,
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalarx" % "0.2.8"
    ,"be.doeraene" %%% "scalajs-jquery" % "0.9.0"
    ,"com.github.japgolly.scalajs-react" %%% "core" % "0.11.1"
    ,"com.github.japgolly.scalajs-react" %%% "extra" % "0.11.1"
    ,"com.github.japgolly.scalacss" %%% "ext-react" % "0.4.1"
    ,"me.chrons" %%% "diode" % "0.5.0"
    ,"me.chrons" %%% "diode-react" % "0.5.0"
    ,"me.chrons" %%% "boopickle" % "1.2.4"
  ),
  jsDependencies ++= Seq(
    "org.webjars.bower" % "react" % "15.2.1"
      /        "react-with-addons.js"
      minified "react-with-addons.min.js"
      commonJSName "React",

    "org.webjars.bower" % "react" % "15.2.1"
      /         "react-dom.js"
      minified  "react-dom.min.js"
      dependsOn "react-with-addons.js"
      commonJSName "ReactDOM",

    "org.webjars.bower" % "react" % "15.2.1"
      /         "react-dom-server.js"
      minified  "react-dom-server.min.js"
      dependsOn "react-dom.js"
      commonJSName "ReactDOMServer",
    "org.webjars" % "startbootstrap-sb-admin-2" % "1.0.8-1"
      /         "sb-admin-2.js"
      //  minified  "sb-dmin-2-min.js"
      dependsOn "bootstrap.js",
    "org.webjars" % "bootstrap" % "3.3.6"
      /         "bootstrap.js"
      dependsOn "dist/jquery.js"
    ,
    "org.webjars" % "metisMenu" % "1.1.2"
      /       "metisMenu.js",
    "org.webjars.bower" % "jQuery" % "2.1.3"
      /         "dist/jquery.js"
      minified  "dist/jquery.min.js"
  )
).enablePlugins(ScalaJSPlugin, SbtWeb)
  .dependsOn(sharedJs)


onLoad in Global := (Command.process("project goyovariantbrowser", _: State)) compose (onLoad in Global).value

//resolvers += "hyracks-releases" at "http://obelix.ics.uci.edu/nexus/content/groups/hyracks-public-releases"
//resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

parallelExecution in Test := false

//libraryDependencies ++= Seq( jdbc , cache , ws   , specs2 % Test )

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )

