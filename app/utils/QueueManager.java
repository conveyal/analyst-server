package utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.opentripplanner.analyst.cluster.AnalystClusterRequest;
import play.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

/** Generic queuing support to enable us to throw stuff into SQS */
public class QueueManager {
	/** maintain a singleton manager */
	private static QueueManager manager;

	/** set up an HTTP client */
	private CloseableHttpClient httpClient = HttpClients.custom()
			.setConnectionManager(new PoolingHttpClientConnectionManager())
			.build();

	private ObjectMapper objectMapper = JsonUtil.getObjectMapper();

	/** per-job callbacks */
	private Multimap<String, Predicate<ResultEnvelope>> callbacks = HashMultimap.create();

	private String broker;

	/** QueueManagers are singletons and thus cannot be constructed directly */
	private QueueManager () {
		broker = "http://localhost:9001";
	}

	/** enqueue an arbitrary number of requests */
	public void enqueue (String userId, String graphId, String jobId, AnalystClusterRequest... requests) {
		enqueue(userId, graphId, jobId, Arrays.asList(requests));
	}

	/** enqueue an arbitrary number of requests */
	public void enqueue (String userId, String graphId, String jobId, Collection<AnalystClusterRequest> requests) {
		// Should we chunk these before sending them?

		// Construct a POST request
		HttpPost req = new HttpPost();
		req.addHeader("Content-Type", "application/json");
		String json;

		try {
			json = objectMapper.writeValueAsString(requests);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		try {
			req.setEntity(new StringEntity(json));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		try {
			req.setURI(new URI(broker + "/" + userId + "/" + graphId + "/" + jobId));
		} catch (URISyntaxException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		CloseableHttpResponse res;
		try {
			res = httpClient.execute(req);
		} catch (IOException e) {
			e.printStackTrace();
			// TODO retry
			throw new RuntimeException(e);
		}

		if (res.getStatusLine().getStatusCode() != 200 && res.getStatusLine().getStatusCode() != 202)
			System.out.println("not ok: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine().getReasonPhrase());

		try {
			res.close();
		} catch (IOException e) {
			e.printStackTrace();
			// recoverable
		}

		req.releaseConnection();

		Logger.info("enqueued {} requests", requests.size());
	}

	/**
	 * Add a callback to a job. Callbacks should return true if they wish to continue receiving results from that
	 * job, false if they wish to be removed.
	 * @param jobId
	 */
	public void addCallback(String jobId, Predicate<ResultEnvelope> callback) {
		this.callbacks.put(jobId, callback);
	}

	/** cancel a job */
	public void cancelJob (String jobId) {
		// TODO dequeue all requests
		this.callbacks.removeAll(jobId);
	}

	public static QueueManager getManager () {
		if (manager == null) {
			synchronized (QueueManager.class) {
				if (manager == null) {
					manager = new QueueManager();
				}
			}
		}

		return manager;
	}
}
