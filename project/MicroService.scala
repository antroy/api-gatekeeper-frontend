import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._

  import TestPhases._

  val appName: String
  val appDependencies : Seq[ModuleID]

  lazy val plugins : Seq[Plugins] = Seq.empty
  lazy val playSettings : Seq[Setting[_]] = Seq.empty

  def unitFilter(name: String): Boolean = !acceptanceFilter(name)

  def acceptanceFilter(name: String): Boolean = name startsWith "acceptance"


  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(play.PlayScala) ++ plugins : _*)
    .settings(playSettings : _*)
    .settings(scalaSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      targetJvm := "jvm-1.8",
      scalaVersion := "2.11.7",
      libraryDependencies ++= appDependencies,
      parallelExecution in Test := false,
      fork in Test := false,
      retrieveManaged := true
    )
    .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
    .settings(testOptions in Test := Seq(Tests.Filter(unitFilter)),
      addTestReportOption(Test, "test-reports"),
      unmanagedSourceDirectories in AcceptanceTest <<= (baseDirectory in AcceptanceTest)(base => Seq(base / "test/unit")),
      unmanagedResourceDirectories in AcceptanceTest <<= (baseDirectory in AcceptanceTest)(base => Seq(base / "test/unit"))
    )
    .configs(AcceptanceTest)
    .settings(inConfig(AcceptanceTest)(Defaults.testSettings): _*)
    .settings(
      testOptions in AcceptanceTest := Seq(Tests.Filter(acceptanceFilter)),
      unmanagedSourceDirectories in AcceptanceTest <<= (baseDirectory in AcceptanceTest)(base => Seq(base / "test")),
      unmanagedResourceDirectories in AcceptanceTest <<= (baseDirectory in AcceptanceTest)(base => Seq(base / "test")),
      Keys.fork in AcceptanceTest := false,
      parallelExecution in AcceptanceTest := false,
      addTestReportOption(AcceptanceTest, "acceptance-test-reports")
    )
    .settings(
      resolvers := Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.typesafeRepo("releases")
      )
    )
    .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
}

private object TestPhases {

  val allPhases = "tt->test;test->test;test->compile;compile->compile"
  val allItPhases = "tit->it;it->it;it->compile;compile->compile"

  lazy val TemplateTest = config("tt") extend Test
  lazy val TemplateItTest = config("tit") extend IntegrationTest
  lazy val AcceptanceTest = config("acceptance") extend Test

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
    }
}

