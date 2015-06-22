package com.conveyal.analyst.server.utils;

import com.conveyal.analyst.server.AnalystMain;
import org.opentripplanner.analyst.cluster.AnalystClusterRequest;
import org.opentripplanner.analyst.cluster.ResultEnvelope;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * Superclass for queue managers.
 */
public abstract class QueueManager {
	/** maintain a singleton manager */
	private static QueueManager manager;

	public void enqueue(AnalystClusterRequest... requests) {
		enqueue(Arrays.asList(requests));
	}

	public abstract void enqueue (Collection<AnalystClusterRequest> requests);

	public abstract ResultEnvelope getSinglePoint (AnalystClusterRequest req)
			throws IOException;

	public abstract void addCallback(String jobId, Predicate<ResultEnvelope> callback);

	public abstract void cancelJob(String jobId);

	public static QueueManager getManager () {
		if (manager == null) {
			synchronized (QueueManager.class) {
				if (manager == null) {
					if (Boolean.FALSE.equals(Boolean.parseBoolean(
							AnalystMain.config.getProperty("cluster.work-offline"))))
						manager = new ClusterQueueManager();
					else
						manager = new LocalQueueManager();
				}
			}
		}

		return manager;
	}
}
