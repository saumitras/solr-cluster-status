name := """SolrClusterStatus"""

version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.apache.solr" % "solr-solrj" % "5.2.1"
)
