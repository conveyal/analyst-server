package com.conveyal.analyst.server.utils;

import com.conveyal.analyst.server.AnalystMain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import models.Query;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.opentripplanner.analyst.broker.JobStatus;
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
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/** Generic queuing support to enable us to throw stuff into SQS */
public class ClusterQueueManager extends QueueManager {
	private static final Logger LOG = LoggerFactory.getLogger(ClusterQueueManager.class);

	/** set up an HTTP client */
	private static final CloseableHttpClient httpClient;

	static {
		PoolingHttpClientConnectionManager mgr = new PoolingHttpClientConnectionManager();
		mgr.setDefaultMaxPerRoute(20);
		httpClient = HttpClients.custom()
			.setConnectionManager(mgr)
			.build();
	}

	/** Configuration for a priority job, includes a long socket timeout to allow for graph building */
	private static final RequestConfig priorityConfig = RequestConfig.custom()
			// ten minutes: marginally long enough to build a New York State graph
			.setSocketTimeout(600 * 1000)
			.setConnectTimeout(10 * 1000)
			.build();

	/** configuration for enqueing requests. Shorter timeout as this should not take long */
	private static final RequestConfig taskConfig = RequestConfig.custom()
			.setSocketTimeout(10 * 1000)
			.setConnectTimeout(10 * 1000)
			.build();


	private ObjectMapper objectMapper = JsonUtil.getObjectMapper();

	/** per-job callbacks */
	private Multimap<String, Predicate<JobStatus>> callbacks = HashMultimap.create();

	private String broker;

	/** The executor used to execute callbacks. Callbacks do things like retrieve results from S3, so should not block the polling thread */
	private Executor executor;

	/** QueueManagers are singletons and thus cannot be constructed directly */
	ClusterQueueManager() {
		broker = AnalystMain.config.getProperty("cluster.broker");

		if (!broker.endsWith("/"))
			broker += "/";

		// set up the executor used to execute callbacks
		// TODO this may be heavier than what is needed
		executor = Executors.newCachedThreadPool();

		// and set up polling
		// TODO use a scheduling engine, e.g. Quartz?
		new Thread(() -> {
			while (true) {
				try {
					if (callbacks.size() > 0) {
						// query across all jobs at once
						String jobs = String.join(",", callbacks.keySet());
						HttpGet get = new HttpGet();
						get.setConfig(taskConfig);

						try {
							get.setURI(new URI(broker + "status/" + jobs));
						} catch (URISyntaxException e) {
							LOG.error("Invalid broker URL");
							throw new RuntimeException(e);
						}

						// don't catch errors here, allow them to be thrown to the next level up
						CloseableHttpResponse res= httpClient.execute(get);

						if (res.getStatusLine().getStatusCode() != 200) {
							LOG.warn("error retrieving job status: " + res.getStatusLine()
									.getStatusCode() + " " + res.getStatusLine().getReasonPhrase());

							if (res.getStatusLine().getStatusCode() == 404) {
								// TODO call every callback
								callbacks.clear();
								continue;
							}
						}

						try {
							InputStream is = res.getEntity().getContent();
							List<JobStatus> stats = objectMapper
									.readValue(is, new TypeReference<List<JobStatus>>() {
									});
							is.close();
							res.close();
							get.releaseConnection();
							stats.forEach(status -> {
								callbacks.get(status.jobId).forEach(cb -> {
									executor.execute(() -> {
										if (!cb.test(status)) {
											callbacks.remove(status.jobId, cb);
										}
									});
								});
							});
						} catch (IOException e) {
							LOG.error("Error retrieving job status", e);
						}
					}
				} catch (Exception e) {
					// don't let the thread crash
					LOG.error("Error retrieving job status", e);
				}

				try {
					Thread.sleep(10000l);
				} catch (InterruptedException e) {
					break;
				}
			}
		}).start();
	}

	/** enqueue an arbitrary number of requests */
	@Override public void enqueue(AnalystClusterRequest... requests) {
		enqueue(Arrays.asList(requests));
	}

	/** enqueue an arbitrary number of requests */
	@Override public void enqueue(Collection<AnalystClusterRequest> requests) {
		// Should we chunk these before sending them?

		for (AnalystClusterRequest clusterRequest : requests) {
			clusterRequest.outputLocation = AnalystMain.config.getProperty("cluster.results-bucket");
		}

		// Construct a POST request
		HttpPost req = new HttpPost();
		req.addHeader("Content-Type", "application/json");
		req.setConfig(taskConfig);
		String json;

		try {
			json = objectMapper.writeValueAsString(requests);
		} catch (JsonProcessingException e) {
			LOG.error("unable to convert requests to JSON", e);
			throw new RuntimeException(e);
		}

		try {
			req.setEntity(new StringEntity(json));
		} catch (UnsupportedEncodingException e) {
			LOG.error("JSON is in unsupported encoding", e);
			throw new RuntimeException(e);
		}

		try {
			req.setURI(new URI(broker + "enqueue/jobs"));
		} catch (URISyntaxException e) {
			LOG.error("Invalid broker URL", e);
			throw new RuntimeException(e);
		}

		CloseableHttpResponse res;
		try {
			res = httpClient.execute(req);
		} catch (IOException e) {
			LOG.error("Network error enqueing requests, trying again in 15 seconds", e);
			executor.execute(() -> {
				try {
					Thread.sleep(15000l);
				} catch (InterruptedException e1) {
					/* do nothing */
				}
				enqueue(requests);
			});
			return;
		}

		if (res.getStatusLine().getStatusCode() != 200 && res.getStatusLine().getStatusCode() != 202)
			LOG.warn("not ok: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine()
					.getReasonPhrase());
		else
			LOG.info("enqueued {} requests", requests.size());

		try {
			res.close();
		} catch (IOException e) {
			LOG.warn("Error closing request (most likely not harmful)", e);
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
		post.setConfig(priorityConfig);

		try {
			post.setURI(new URI(broker + "enqueue/priority"));
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
	 * Add a callback to a job. Callbacks should return true if they wish the job to continue, false
	 * otherwise.
	 * @param jobId
	 */
	@Override public void addCallback(String jobId, Predicate<JobStatus> callback) {
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
			req.setURI(new URI(broker + "/jobs/" + jobId));

			CloseableHttpResponse res = httpClient.execute(req);

			if (res.getStatusLine().getStatusCode() != 200 && res.getStatusLine().getStatusCode()
					!= 202)
				LOG.warn(
						"not ok: " + res.getStatusLine().getStatusCode() + " " + res.getStatusLine()
								.getReasonPhrase());

			res.close();
			req.releaseConnection();
		} catch (URISyntaxException | IOException e) {
			LOG.error("error canceling job", e);
		}
	}
}
