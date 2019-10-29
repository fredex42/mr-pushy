import com.typesafe.sbt.packager.rpm.RpmPlugin.autoImport.rpmLicense
import com.typesafe.sbt.packager.docker
import com.typesafe.sbt.packager.docker._
import sbt._
import Keys._

name := "upload_flush_list_mt"

version := "1.0.0"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  // https://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk-s3
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.372",
  "com.amazonaws" % "aws-java-sdk-cloudwatchmetrics" % "1.11.372",
  // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  // https://mvnrepository.com/artifact/ch.qos.logback/logback-core
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.25",
  //testing
  // https://mvnrepository.com/artifact/org.specs2/specs2-core
  "org.specs2" %% "specs2-core" % "4.3.2" % Test,
  // https://mvnrepository.com/artifact/org.specs2/specs2-junit
  "org.specs2" %% "specs2-junit" % "4.3.2" % Test,
  "org.specs2" %% "specs2-mock" % "4.3.2" % Test,
  "org.mockito" % "mockito-all" % "1.10.19" % Test
)

testOptions in Test ++= Seq(
  Tests.Argument("junitxml", "junit.outdir", sys.env.getOrElse("SBT_JUNIT_DIR",".")),
  Tests.Argument("console")
)

rpmVendor := "theguardian"
rpmLicense := Some("GPLv3")

lazy val app = (project in file("."))
  .enablePlugins(JavaServerAppPackaging, RpmPlugin, AshScriptPlugin, DockerPlugin)
  .settings(
    version in Rpm := version.value,
    rpmRelease := sys.env.getOrElse("CIRCLE_BUILD_NUM","SNAPSHOT"),
    mainClass in Compile := Some("Main"),
    dockerUsername  := sys.props.get("docker.username"),
    dockerRepository := sys.props.get("docker.host"),
    dockerPermissionStrategy := DockerPermissionStrategy.None,
    packageName in Docker := s"${sys.props.get("docker.username")}/mrpushy",
    packageName := "mrpushy",
    //    dockerBaseImage := "openjdk:8-jdk-alpine",
    dockerAlias := docker.DockerAlias(sys.props.get("docker.host"),sys.props.get("docker.username"),"mr-pushy",Some(sys.props.getOrElse("build.number","DEV"))),
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "chown -R 1001 /opt/docker"),
      Cmd("USER", "demiourgos728")
    )
  )


