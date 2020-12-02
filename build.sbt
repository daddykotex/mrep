lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "com.daddykotex",
        scalaVersion := "2.13.4",
        //scalafix settings
        semanticdbEnabled := true,
        semanticdbVersion := scalafixSemanticdb.revision,
        scalafixDependencies += "com.nequissimus" %% "sort-imports" % "0.5.5"
      )
    ),
    name := "mrep"
  )
  .aggregate(mrepCli)

lazy val debugOptionsNativeImage = Seq(
  "-H:+ReportExceptionStackTraces",
  "-H:+ReportUnsupportedElementsAtRuntime",
  "-H:+TraceClassInitialization",
  "-H:+PrintClassInitialization",
  "-H:+StackTrace",
  "-H:+JNI",
  "-H:-SpawnIsolates",
  "-H:-UseServiceLoaderFeature",
  "-H:+RemoveSaturatedTypeFlows"
)

lazy val buildInfoSettings = Def.settings(
  buildInfoKeys := Seq[BuildInfoKey](name, version),
  buildInfoPackage := "com.daddykotex.mrep.build"
)

addCommandAlias("buildCli", "mrepCli/nativeImage")
lazy val mrepCli = (project in file("mrep-cli"))
  .enablePlugins(NativeImagePlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings)
  .settings(
    name := "mrep-cli",
    Compile / mainClass := Some("com.daddykotex.mrep.Main"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"           % Versions.catsCore,
      "org.http4s"    %% "http4s-blaze-client" % Versions.http4s,
      "com.monovore"  %% "decline"             % Versions.declineVersion,
      "com.monovore"  %% "decline-effect"      % Versions.declineVersion,
      "org.scalameta" %% "munit"               % Versions.munit     % Test,
      "org.typelevel" %% "munit-cats-effect-2" % Versions.catsMunit % Test,
      "org.http4s"    %% "http4s-dsl"          % Versions.http4s    % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    nativeImageOptions ++= List(
      "--verbose",
      "--no-server",
      "--no-fallback",
      "--enable-http",
      "--enable-https",
      "--enable-all-security-services",
      "--report-unsupported-elements-at-runtime",
      "--allow-incomplete-classpath",
      "--initialize-at-build-time=scala,org.slf4j.LoggerFactory"
    )
  )
