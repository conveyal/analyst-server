package com.conveyal.analyst.server.utils;

import com.conveyal.analyst.server.AnalystMain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import models.Query;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.opentripplanner.analyst.cluster.AnalystClusterRequest;
import org.opentripplanner.analyst.cluster.ResultEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

/** Generic queuing support to enable us to throw stuff into SQS */
public class ClusterQueueManager extends QueueManager {
	private static final Logger LOG = LoggerFactory.getLogger(ClusterQueueManager.class);

	/** set up an HTTP client */
	private CloseableHttpClient httpClient = HttpClients.custom()
			.setConnectionManager(new PoolingHttpClientConnectionManager())
			.build();

	private ObjectMapper objectMapper = JsonUtil.getObjectMapper();

	/** per-job callbacks */
	private Multimap<String, Predicate<ResultEnvelope>> callbacks = HashMultimap.create();

	private String broker;

	/** QueueManagers are singletons and thus cannot be constructed directly */
	ClusterQueueManager() {
		broker = AnalystMain.config.getProperty("cluster.broker");

		if (!broker.endsWith("/"))
			broker += "/";
	}

	/** enqueue an arbitrary number of requests */
	@Override public void enqueue(AnalystClusterRequest... requests) {
		enqueue(Arrays.asList(requests));
	}

	/** enqueue an arbitrary number of requests */
	@Override public void enqueue(Collection<AnalystClusterRequest> requests) {
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
			req.setURI(new URI(broker + "tasks"));
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
			LOG.warn("not ok: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine()
					.getReasonPhrase());
		else
			LOG.info("enqueued {} requests", requests.size());

		try {
			res.close();
		} catch (IOException e) {
			e.printStackTrace();
			// recoverable
		}

		req.releaseConnection();

	}

	/** Get a single point job */
	@Override public ResultEnvelope getSinglePoint(AnalystClusterRequest req)
			throws IOException {
		String json = objectMapper.writeValueAsString(req);
		HttpPost post = new HttpPost();
		post.setHeader("Content-Type", "application/json");

		try {
			post.setURI(new URI(broker + "priority"));
		} catch (URISyntaxException e) {
			LOG.error("Malformed broker URI {}, analysis will not be possible", broker);
			return null;
		}

		post.setEntity(new StringEntity(json));

		CloseableHttpResponse res = httpClient.execute(post);

		if (res.getStatusLine().getStatusCode() != 200 && res.getStatusLine().getStatusCode() != 202)
			LOG.warn("not ok: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine()
					.getReasonPhrase());

		// read the response
		InputStream is = res.getEntity().getContent();
		ResultEnvelope re = objectMapper.readValue(is, ResultEnvelope.class);
		is.close();

		res.close();
		post.releaseConnection();

		return re;
	}

	/**
	 * Add a callback to a job. Callbacks should return true if they wish to continue receiving results from that
	 * job, false if they wish to be removed.
	 * @param jobId
	 */
	@Override public void addCallback(String jobId, Predicate<ResultEnvelope> callback) {
		this.callbacks.put(jobId, callback);
	}

	/** cancel a job */
	@Override public void cancelJob(String jobId) {
		this.callbacks.removeAll(jobId);

		Query q = Query.getQuery(jobId);

		// figure out the graph ID
		String graphId = q.getGraphId();

		try {
			HttpDelete req = new HttpDelete();
			req.setURI(new URI(broker + "/" + q.projectId + "/" + graphId + "/" + jobId));

			CloseableHttpResponse res = httpClient.execute(req);

			if (res.getStatusLine().getStatusCode() != 200 && res.getStatusLine().getStatusCode()
					!= 202)
				LOG.warn(
						"not ok: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine()
								.getReasonPhrase());

			res.close();
			req.releaseConnection();
		} catch (URISyntaxException | IOException e) {
			e.printStackTrace();
		}
	}
}
