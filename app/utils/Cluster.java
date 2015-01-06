package utils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import play.Configuration;
import play.Logger;
import play.Play;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import com.conveyal.otpac.actors.Executive;
import com.conveyal.otpac.message.JobStatus;
import com.conveyal.otpac.message.JobStatusQuery;
import com.conveyal.otpac.workers.ThreadWorkerFactory;
import com.conveyal.otpac.workers.WorkerFactory;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;

public class Cluster {
	private static ActorSystem actorSystem = null;
	private static ActorRef executive = null;
	
	public static ActorSystem getActorSystem () {
		// lazy-initialize the actor system
		if (actorSystem == null) {
			// the config isn't large, so it's fine to just copy it to a string
			StringWriter sw = new StringWriter();
			
			Map<String, Object> akkaConfig;
			
			Configuration akkaRemote = Play.application().configuration().getConfig("cluster.akka");
			
			if (akkaRemote != null)
				akkaConfig = akkaRemote.asMap();
			else
				akkaConfig = new HashMap<String, Object>();
			
			// set up S3 credentials, but not if we're working offline
			boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline", true);
			if (!workOffline) {
				String s3cred = Play.application().configuration().getString("cluster.s3credentials");
				
				if (s3cred != null && !Play.application().configuration().getBoolean("cluster.work-offline")) {
					// add the s3 credentials to the local akka system config
					Map<String, ImmutableMap<String, String>> s3 = ImmutableMap.of(
							"credentials", ImmutableMap.of("filename", s3cred)
							);
					
					akkaConfig.put("s3", s3);
				}
			}
			
			Config cfg = ConfigFactory.parseMap(akkaConfig);
			
			actorSystem = ActorSystem.create("analyst-server", cfg); 
		}
		
		return actorSystem;
	}
	
	public static ActorRef getExecutive () {
		if (executive == null) {
			ActorSystem sys = getActorSystem();
			
			boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline", true);
			
			String graphsBucket = Play.application().configuration().getString("cluster.graphs-bucket");
			String pointsetsBucket = Play.application().configuration().getString("cluster.pointsets-bucket");
			
			executive = sys.actorOf(Props.create(Executive.class, workOffline, graphsBucket, pointsetsBucket), "executive");
			
			if (workOffline) {
				// give it a worker
				WorkerFactory factory = new ThreadWorkerFactory(sys, workOffline, graphsBucket, pointsetsBucket);
				factory.createWorkerManagers(1, executive);
			}
			else {
				Logger.info("Started executive, but no workers started. Start a cluster worker to see analysis results.");
			}
		}
		
		return executive;
	}

	/**
	 * Get the status of the given job.
	 */
	public static JobStatus getStatus(int jobId) {
		Timeout timeout = new Timeout(Duration.create(5, "seconds"));
		Future<Object> future = Patterns.ask(executive, new JobStatusQuery(), timeout);
		ArrayList<JobStatus> result;
		try {
			result = (ArrayList<JobStatus>) Await.result(future, timeout.duration());
		} catch (Exception e) {
			return null;
		}
		
		for (JobStatus s : result) {
			if (s.curJobId == jobId) {
				return s;
			}
		}
		
		return null;
	}
}
