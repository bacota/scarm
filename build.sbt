scalaVersion := "2.12.4"

scalacOptions ++= Seq("-Ypartial-unification", "-deprecation", "-feature")

val doobieVersion = "0.5.1"

libraryDependencies ++= Seq(

  "org.tpolecat" %% "doobie-h2"        % doobieVersion, // H2 driver 1.4.196 + type mappings.
  "org.tpolecat" %% "doobie-core"      % doobieVersion,
  "org.tpolecat" %% "doobie-hikari"    % doobieVersion, // HikariCP transactor.
  "org.tpolecat" %% "doobie-postgres"  % doobieVersion, // Postgres driver 42.1.4 + type mappings.
//  "org.tpolecat" %% "doobie-specs2"    % doobieVersion, // Specs2 support for typechecking statements.
  "org.tpolecat" %% "doobie-scalatest" % doobieVersion,  // ScalaTest support for typechecking statements.
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

testOptions in Test += Tests.Argument("-oDF")
