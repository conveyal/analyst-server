package utils;

import java.util.ArrayList;

import play.Play;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import com.conveyal.otpac.actors.Executive;
import com.conveyal.otpac.message.JobStatus;
import com.conveyal.otpac.message.JobStatusQuery;
import com.conveyal.otpac.workers.ThreadWorkerFactory;
import com.conveyal.otpac.workers.WorkerFactory;
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
			Config cfg = ConfigFactory.load();
			actorSystem = ActorSystem.create("analyst-server", cfg); 
		}
		
		return actorSystem;
	}
	
	public static ActorRef getExecutive () {
		if (executive == null) {
			ActorSystem sys = getActorSystem();
			
			Boolean workOffline = Play.application().configuration().getBoolean("cluster.work-offline");
			
			if (workOffline == null)
				workOffline = true;
			
			String graphsBucket = Play.application().configuration().getString("cluster.graphs-bucket");
			String pointsetsBucket = Play.application().configuration().getString("cluster.pointsets-bucket");
			
			executive = sys.actorOf(Props.create(Executive.class, workOffline, graphsBucket, pointsetsBucket), "executive");
			
			if (workOffline) {
				// give it a worker
				WorkerFactory factory = new ThreadWorkerFactory(sys, workOffline, graphsBucket, pointsetsBucket);
				factory.createWorkerManagers(1, executive);
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
