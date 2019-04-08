name := "daml-on-sawtooth"
version := "0.0.1"
scalaVersion := "2.12.8"

val damlSdkVersion = "100.11.19"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "io.grpc" % "grpc-netty" % "1.18.0",
  "io.grpc" % "grpc-services" % "1.11.0",
  "io.spray" %%  "spray-json" % "1.3.4",
  "com.typesafe" % "config" % "1.2.1",
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "com.typesafe.akka" %% "akka-stream" % "2.5.12",
  "org.reactivestreams" % "reactive-streams" % "1.0.2",
  "org.scalaz" %% "scalaz-concurrent" % "7.2.16",
  "org.scalaz" %% "scalaz-core" % "7.2.16",

  "com.thesamet.scalapb" %% "scalapb-runtime" % "0.8.4",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % "0.8.4",

  "com.daml.ledger" % "bindings-java" % "2.3.3",
  "com.daml.scala" %% "akka-stream-client" % damlSdkVersion,

  "com.digitalasset" % "daml-lf-value-java-proto" % damlSdkVersion,
  "com.digitalasset" %% "daml-lf-engine" % damlSdkVersion,
  "com.digitalasset" %% "daml-lf-data" % damlSdkVersion,
  "com.digitalasset" %% "daml-lf-interpreter" % damlSdkVersion,
  "com.digitalasset" %% "daml-lf-package" % damlSdkVersion,
  "com.digitalasset" %% "daml-lf-transaction" % damlSdkVersion,
  "com.digitalasset" %% "daml-lf-validation" % damlSdkVersion,
  "com.digitalasset" %% "daml-lf-archive-scala" % damlSdkVersion,
  "com.digitalasset.ledger-bindings" %% "bindings-scala" % damlSdkVersion,
  "com.digitalasset.ledger-bindings" %% "bindings-scala-logging" % damlSdkVersion,
  "com.digitalasset.ledger-bindings" %% "bindings-akka" % damlSdkVersion,

  "com.digitalasset.ledger" %% "ledger-backend-api" % damlSdkVersion,
  "com.digitalasset.ledger" %% "ledger-api-client" % damlSdkVersion,
  "com.digitalasset.ledger" %% "ledger-api-common" % damlSdkVersion,
  "com.digitalasset.ledger" %% "ledger-api-domain" % damlSdkVersion,

  "com.digitalasset.ledger-api" % "rs-grpc-bridge" % damlSdkVersion,
  "com.digitalasset.ledger-api" %% "rs-grpc-akka" % damlSdkVersion,

  "com.digitalasset.platform" % "sandbox" % damlSdkVersion,

  "com.digitalasset.ledger" % "semantic-test-runner" % damlSdkVersion % Test)

resolvers ++= Seq(
  Resolver.mavenLocal,
  "da repository" at "https://digitalassetsdk.bintray.com/DigitalAssetSDK"
)
