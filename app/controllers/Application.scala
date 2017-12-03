package controllers

import play.api.Logger
import play.api.mvc.Action
import play.api.mvc.Controller

import org.apache.solr.client.solrj.impl.CloudSolrClient
import sys.process._
import scala.collection.JavaConversions._

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def clusterStatus(zkHost:String) = Action {
    val status = isClusterReady(zkHost)
    Ok(status.toString)
  }

  def isClusterReady(zkHost: String):Boolean = {
    val zkStatus = getZkServerStatus(zkHost).forall(_._2 == true)

    lazy val solrCollectionStatus = getSolrCollectionStatus(zkHost) match {
      case Some(cols) =>
        val unhealthyCols = cols.count(_._2 == false)
        if(unhealthyCols > 0) {
          Logger.error(s"Found $unhealthyCols collection(s) in bad state. Fix the cluster manually.")
        }
        cols.forall(_._2 == true)
      case None =>
        Logger.error("Failed to get solr cluster information")
        false
    }

    zkStatus && solrCollectionStatus
  }

  def getZkServerStatus(zkHost: String):Map[String, Boolean] = {
    val zkServers = zkHost.replaceAll("/.*","").split(",") //replace any chroot
    val status = zkServers.map { server =>
      val hostname = server.replaceAll(":.*","")
      val port = server.replaceAll(".*:","")

      //more commands can be added here like leader check, max/avg latency check
      val cmd = "echo ruok" #| s"nc $hostname $port"

      try {
        val res = cmd.!!.contains("imok")
        Logger.info(s"zk $server healthy = $res")
        Map(server -> res)
      } catch {
        case ex:Exception =>
          Logger.error(s"Failed to get status of zk server=$server. Exception: ${ex.getMessage}")
          Map(server -> false)
      }
    }
    status.reduceOption(_ ++ _).getOrElse(Map())
  }

  def getSolrCollectionStatus(zkHost: String) = {
    try {
      val zkClient = new CloudSolrClient(zkHost)
      zkClient.connect()
      val cluster = zkClient.getZkStateReader.getClusterState
      val colStatus = cluster.getCollections.toList.map { collection =>
        val replicas = cluster.getCollection(collection).getSlicesMap.toMap
        val replicaStatus = replicas.map { replica =>
          val leaderState = if(replica._2.getLeader == null) "DOWN"
                            else replica._2.getLeader.getState.toString.toUpperCase
          leaderState == "ACTIVE"
        }
        Map(collection -> replicaStatus.forall(_ == true))
      }
      Some(colStatus.reduceOption(_ ++ _).getOrElse(Map()))
    } catch {
      case ex:Exception =>
        ex.printStackTrace()
        None
    }

  }

}
