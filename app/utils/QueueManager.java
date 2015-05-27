package utils;

import akka.actor.Cancellable;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.*;
import com.beust.jcommander.internal.Lists;
import com.beust.jcommander.internal.Sets;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import models.Query;
import models.TransportScenario;
import org.opentripplanner.routing.core.TraverseModeSet;
import play.Logger;
import play.Play;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.awt.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/** Generic queuing support to enable us to throw stuff into SQS */
public class QueueManager {
	/** maintain a singleton manager */
	private static QueueManager manager;

	/** The prefix to use for these queues */
	private final String prefix;

	/** The SQS client */
	private final AmazonSQSClient sqs;

	/** The S3 client */
	private final AmazonS3Client s3;

	private static final ObjectMapper objectMapper = new ObjectMapper();

	/** The key is a rudimentary level of security. It is included as a GET param in all responses from the cluster. */
	public final String key = IdUtils.getId();

	/** When we delete a query, there may still be jobs being enqueued for it. Make sure we drop those on the floor. */
	public Set<String> deletedQueries = Sets.newHashSet();

	static {
		objectMapper.registerModule(new JodaModule());
		objectMapper.registerModule(new TraverseModeSetModule());
	}

	/** S3 bucket in which results are placed */
	private final String outputLoc = Play.application().configuration().getString("cluster.results-bucket");

	private final ConcurrentMap<String, Consumer<ResultEnvelope>> callbacks = new ConcurrentHashMap<>();

	/** Per-job callbacks */
	private final ConcurrentHashMap<String, Predicate<ResultEnvelope>> jobCallbacks = new ConcurrentHashMap<>();

	private final String outputQueue;

	/** Are we currently polling? */
	private boolean polling = false;

	/** Create a queue manager with credentials and prefixes taken from the environment */
	public QueueManager () {
		prefix = Play.application().configuration().getString("cluster.queue-prefix");

		// create the SQS client
		String credentialsFilename = Play.application().configuration().getString("cluster.aws-credentials");

		if (credentialsFilename != null) {
			AWSCredentials creds = new ProfileCredentialsProvider(credentialsFilename, "default").getCredentials();
			sqs = new AmazonSQSClient(creds);
			s3 = new AmazonS3Client(creds);
		} else {
			// default credentials providers, e.g. IAM role
			sqs = new AmazonSQSClient();
			s3 = new AmazonS3Client();
		}

		outputQueue = createQueue(prefix + "_output_" + IdUtils.getId());
	}

	/** Enqueue a request with no callback */
	public boolean enqueue (AnalystClusterRequest req) {
		return enqueue(req, null);

	}

	public boolean enqueue (AnalystClusterRequest req, Consumer<ResultEnvelope> callback) {
		String queueUrl;

		// single point job, not associated with a parent job
		if (req.jobId == null)
			queueUrl = getOrCreateSinglePointQueue(req.graphId);
		else {
			// don't enqueue jobs for deleted queries
			if (deletedQueries.contains(req.jobId))
				return false;

			queueUrl = getOrCreateJobQueue(req.graphId, req.jobId);
		}

		// store the callback
		if (callback != null)
			callbacks.put(req.jobId + "_" + req.id, callback);

		// wire up the routing to return the results
		if (req.jobId == null)
			// single point request
			req.directOutputUrl = Play.application().configuration().getString("cluster.url-internal") + "/api/result?key=" + key;
		else {
			// multipoint, accumulate in S3
			req.outputLocation = outputLoc;
			req.outputQueue = outputQueue;
		}

		// enqueue it
		try {
			String json = objectMapper.writeValueAsString(req);
			sqs.sendMessage(queueUrl, json);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}


	/** Batch-enqueue many requests. Assumes all have the same jobId. */
	public boolean enqueueBatch(Collection<AnalystClusterRequest> requests) {
		if (requests.isEmpty()) return true;

		AnalystClusterRequest exemplar = requests.iterator().next();


		String queueUrl;

		// single point job, not associated with a parent job
		if (exemplar.jobId == null)
			queueUrl = getOrCreateSinglePointQueue(exemplar.graphId);
		else {
			// don't enqueue jobs for deleted queries
			if (deletedQueries.contains(exemplar.jobId))
				return false;

			queueUrl = getOrCreateJobQueue(exemplar.graphId, exemplar.jobId);
		}

		// make job collections
		List<SendMessageBatchRequest> batches = Lists.newArrayList();

		Iterator<AnalystClusterRequest> it = requests.iterator();

		// batch the requests
		while (it.hasNext()) {
			SendMessageBatchRequest current = new SendMessageBatchRequest();
			current.setQueueUrl(queueUrl);

			int count = 0;
			List<SendMessageBatchRequestEntry> entries = Lists.newArrayList();

			while (it.hasNext() && count < 10) {
				AnalystClusterRequest req = it.next();

				// wire up the routing to return the results
				if (req.jobId == null)
					// single point request
					req.directOutputUrl = Play.application().configuration().getString("cluster.url-internal") + "/api/result?key=" + key;
				else {
					// multipoint, accumulate in S3
					req.outputLocation = outputLoc;
					req.outputQueue = outputQueue;
				}

				String json;
				try {
					json = objectMapper.writeValueAsString(req);
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					return false;
				}

				SendMessageBatchRequestEntry entry = new SendMessageBatchRequestEntry();
				entry.setMessageBody(json);
				entry.setId("" + count);
				entries.add(entry);

				count++;
			}

			current.setEntries(entries);
			batches.add(current);
		}

		// enqueue in parallel.
		batches.parallelStream().forEach(batch -> sqs.sendMessageBatch(batch));

		return true;
	}

	private String getOrCreateJobQueue(String graphId, String jobId) {
		String queueName = getMultipointQueueName(graphId, jobId);
		return getOrCreateQueue(queueName);
	}

	/** Get the URL for a single-point queue, creating the queue if necessary */
	private String getOrCreateSinglePointQueue(String graphId) {
		String queueName = prefix + "_" + graphId + "_single";
		return getOrCreateQueue(queueName);
	}

	/** Get the URL for the named queue, creating it if necessary */
	private String getOrCreateQueue(String queueName) {
		try {
			GetQueueUrlResult res = sqs.getQueueUrl(queueName);
			return res.getQueueUrl();
		} catch (QueueDoesNotExistException e) {
			return createQueue(queueName);
		}
	}
	
	private String getMultipointQueueName (String graphId, String jobId) {
		return prefix + "_" + graphId + "_" + jobId;
	}

	/** Create the queue with the given name */
	private String createQueue(String queueName) {
		// TODO: visibility timeout defaults to 30s, which is probably about right as we shouldn't be
		// waiting for a graph build. IAM policy should perhaps be set here?
		// TODO: Set up redrive policy for all queues.
		CreateQueueRequest cqr = new CreateQueueRequest(queueName);
		CreateQueueResult res = sqs.createQueue(cqr);
		return res.getQueueUrl();
	}

	/** maintain a single instance of QueueManager */
	public static QueueManager getManager () {
		if (manager == null) {
			synchronized(QueueManager.class) {
				manager = new QueueManager();
			}
		}

		return manager;
	}

	/**
	 * Register a callback for a particular job. Will be called for each result until it returns false.
	 */
	public void registerJobCallback (Query q, Predicate<ResultEnvelope> callback) {
		jobCallbacks.put(q.id, callback);
		if (!polling) {
			synchronized (this) {
				if (!polling) {
					Akka.system().scheduler().scheduleOnce(
							Duration.create(1, TimeUnit.SECONDS),
							new QueryListener(),
							Akka.system().dispatcher()
					);
				}
			}
		}
	}
	
	/** Cancel an in-progress job */
	public void cancelJob (Query q) {
		deletedQueries.add(q.id);

		if (jobCallbacks.containsKey(q.id))
			jobCallbacks.remove(q.id);

		TransportScenario s = TransportScenario.getScenario(q.scenarioId);
		
		deleteQueue(getMultipointQueueName(s.bundleId, q.id));
	}

	/** Delete a queue if it exists */
	private void deleteQueue(String queueName) {
		String queueUrl;

		try {
			queueUrl = sqs.getQueueUrl(queueName).getQueueUrl();
		} catch (QueueDoesNotExistException e) {
			return;
		}

		DeleteQueueRequest dqr = new DeleteQueueRequest();
		dqr.setQueueUrl(queueUrl);
		sqs.deleteQueue(dqr);
	}

	/** Handle a result envelope that's come back from the cluster */
	public void handle (ResultEnvelope env) {
		Consumer<ResultEnvelope> callback = callbacks.get(env.jobId + "_" + env.id);

		if (callback == null) return;

		// don't let a bad callback crash the whole enterprise
		try {
			callback.accept(env);
		} catch (Exception e) {
			e.printStackTrace();
		}

		callbacks.remove(env.jobId + "_" + env.id);
	}

	public static class TraverseModeSetSerializer extends JsonSerializer<TraverseModeSet> {
		@Override
		public void serialize(TraverseModeSet traverseModeSet, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
			jsonGenerator.writeString(traverseModeSet.toString());
		}
	}

	public static class TraverseModeSetModule extends SimpleModule {
		public TraverseModeSetModule () {
			super("TraverseModeSetModule", new Version(0, 0, 1, null, null, null));
		}

		@Override
		public void setupModule(SetupContext ctx) {
			SimpleSerializers s = new SimpleSerializers();
			s.addSerializer(TraverseModeSet.class, new TraverseModeSetSerializer());
			ctx.addSerializers(s);
		}
	}

	/** Listen for multipoint query results */
	// not static so that we can access instance variables of parent class
	public class QueryListener implements Runnable {
		@Override
		public void run() {
			polling = true;

			// no point in waiting for results if there is nothing to do with them when they arrive.
			// we use a loop here rather than an Akka interval because if there are messages in the queue we want to
			// poll as fast as we possibly can. If there are no messages in the queue this loop will run every 20s
			// because we're using blocking long polling.
			while (true) {
				if (jobCallbacks.size() == 0) {
					polling = false;
					return;
				}

				ReceiveMessageRequest req = new ReceiveMessageRequest();
				req.setMaxNumberOfMessages(10);
				req.setWaitTimeSeconds(20);
				req.setQueueUrl(outputQueue);
				ReceiveMessageResult res = sqs.receiveMessage(req);

				List<Message> messagesToDelete = Lists.newArrayList();

				// process messages in parallel due to S3 delays.
				// However there is a sync block around the storage callback so that that doesn't get called in parallel
				MESSAGES:
				res.getMessages().parallelStream().forEach(msg -> {
					PointDone body;
					try {
						body = objectMapper.readValue(msg.getBody(), PointDone.class);
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}

					Predicate<ResultEnvelope> cb = jobCallbacks.get(body.jobId);
					if (cb != null) {
						// get the result envelope from S3
						ResultEnvelope re;
						try {
							S3Object obj = s3.getObject(outputLoc, body.toString());
							InputStream is = obj.getObjectContent();
							GZIPInputStream gis = new GZIPInputStream(new BufferedInputStream(is));
							re = objectMapper.readValue(gis, ResultEnvelope.class);
							is.close();
						} catch (IOException e) {
							e.printStackTrace();
							return;
						}

						// call the callback, catching exceptions if they occur, and remove the callback if it returns false.
						boolean shouldJobContinue = true;
						try {
							// call the callback sequentially; the slow part is getting results from S3. Storing them needn't be done in parallel
							synchronized (cb) {
								shouldJobContinue = cb.test(re);
							}
						} catch (Exception e) {
							return;
						}

						// FIXME: results with no callback defined will never be deleted!
						// Two possible solutions: a) delete these messages (but what if someone adds a callback for them later?)
						// b) add a redrive policy to the output queue so that these messages are sent to dead letters.
						messagesToDelete.add(msg);

						// if the job is done, remove the callback
						// note that we are not deleting results here; they will be removed by a lifecycle config in
						// S3.
						if (!shouldJobContinue) {
							jobCallbacks.remove(body.jobId);
						}
					}
				});

				// delete the messages that were successfully processed
				if (!messagesToDelete.isEmpty()) {
					DeleteMessageBatchRequest dmbr = new DeleteMessageBatchRequest();
					dmbr.setEntries(messagesToDelete.stream().map(msg -> {
						DeleteMessageBatchRequestEntry dmr = new DeleteMessageBatchRequestEntry();
						dmr.setId(msg.getMessageId());
						dmr.setReceiptHandle(msg.getReceiptHandle());
						return dmr;
					}).collect(Collectors.toList()));
					dmbr.setQueueUrl(outputQueue);

					sqs.deleteMessageBatch(dmbr);
				}
			}
		}
	}

	/** One point of a multipoint job is done */
	public static class PointDone {
		public String jobId;
		public String id;

		public String toString () {
			if (jobId != null)
				return jobId + "/" + id + ".json.gz";
			else
				return id + ".json.gz";
		}
	}
}
