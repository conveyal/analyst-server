package com.conveyal.analyst.server.utils;

import com.conveyal.analyst.server.AnalystMain;
import com.conveyal.r5.analyst.broker.JobStatus;
import com.conveyal.r5.analyst.cluster.AnalystClusterRequest;
import com.conveyal.r5.analyst.cluster.GenericClusterRequest;
import com.conveyal.r5.analyst.cluster.ResultEnvelope;

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

	public abstract byte[] getGenericRequest (GenericClusterRequest req) throws IOException;

	/** Add a callback for job status. Note that it may be called multiple times in parallel if it takes a long time to return */
	public abstract void addCallback(String jobId, Predicate<JobStatus> callback);

	public abstract void cancelJob(String jobId);

	public static QueueManager getManager () {
		if (manager == null) {
			synchronized (QueueManager.class) {
				if (manager == null) {
					// AnalystMain.config.getProperty("cluster.work-offline")
					// Doesn't affect anything here anymore, we only use the ClusterQueueManager
					manager = new ClusterQueueManager();
				}
			}
		}

		return manager;
	}
}
