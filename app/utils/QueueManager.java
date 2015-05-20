package utils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.*;
import com.beust.jcommander.internal.Lists;
import com.beust.jcommander.internal.Maps;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import play.Logger;
import play.Play;
import play.libs.Akka;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Generic queuing support to enable us to throw stuff into SQS */
public class QueueManager {
	/** maintain a singleton manager */
	private static QueueManager manager;

	/** The prefix to use for these queues */
	private final String prefix;
	
	/** The SQS client */
	private final AmazonSQSClient sqs;

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private boolean polling;

	static {
		objectMapper.registerModule(new JodaModule());
	}

	/** URL of queue that brings single point results back from the cluster */
	private final String outputQueue;

	private final ConcurrentMap<String, Consumer<ResultEnvelope>> callbacks = new ConcurrentHashMap<>();

	/** Create a queue manager with credentials and prefixes taken from the environment */
	public QueueManager () {
		prefix = Play.application().configuration().getString("cluster.queue-prefix");

		// create the SQS client
		String credentialsFilename = Play.application().configuration().getString("cluster.aws-credentials");

		if (credentialsFilename != null) {
			AWSCredentials creds = new ProfileCredentialsProvider(credentialsFilename, "default").getCredentials();
			sqs = new AmazonSQSClient(creds);
		} else {
			// default credentials providers, e.g. IAM role
			sqs = new AmazonSQSClient();
		}

		// create the output queue
		String outQueueName = prefix + "-output-" + UUID.randomUUID().toString();
		outputQueue = createQueue(outQueueName);
		Logger.info("Receiving single point results from queue " + outQueueName);

		// listen to the output queue
		Akka.system().scheduler().scheduleOnce(new FiniteDuration(1, TimeUnit.SECONDS), new QueueListener(), Akka.system().dispatcher());
	}
	
	/** Enqueue a single point request */
	public boolean enqueue (AnalystClusterRequest req) {
		return enqueue(req, null);

	}

	public boolean enqueue (AnalystClusterRequest req, Consumer<ResultEnvelope> callback) {
		String queueUrl = getOrCreateSinglePointQueue(req.graphId);

		// state tracking
		req.jobId = null;
		req.id = "single-" + UUID.randomUUID().toString();
		req.disposition = AnalystClusterRequest.RequestDisposition.ENQUEUE;
		req.outputLocation = outputQueue;

		// store the callback
		callbacks.put(req.id, callback);

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

	/** Get the URL for a single-point queue, creating the queue if necessary */
	private String getOrCreateSinglePointQueue(String graphId) {
		String queueName = prefix + "-single-" + graphId;
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

	/** Create the queue with the given name */
	private String createQueue(String queueName) {
		// TODO: visibility timeout defaults to 30s, which is probably about right as we shouldn't be
		// waiting for a graph build. IAM policy should perhaps be set here?
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

	/** Listen to the queue and call callbacks as needed */
	public class QueueListener implements Runnable {
		@Override
		public void run() {
			while (true) {
				// don't poll unless we're expecting jobs back
				if (callbacks.isEmpty()) {
					try {
						// only sleep for 1s so we don't miss new jobs being enqueued
						Thread.sleep(1000l);
					} catch (InterruptedException e) {
						break;
					}
					continue;
				}

				ReceiveMessageRequest rmr = new ReceiveMessageRequest();
				// long poll so we have fewer requests and are sure to get responses
				// TODO does this mean we wait 20 seconds to get any response even if messages are
				// available before that? if so this is a non-starter.
				rmr.setWaitTimeSeconds(20);
				rmr.setMaxNumberOfMessages(10);

				rmr.setQueueUrl(outputQueue);

				ReceiveMessageResult res = sqs.receiveMessage(rmr);

				List<String> messagesToDelete = Lists.newArrayList();

				for (Message message : res.getMessages()) {
					String json = message.getBody();
					// deserialize to result envelope
					ResultEnvelope env;
					try {
						env = objectMapper.readValue(json, ResultEnvelope.class);
					} catch (IOException e) {
						Logger.error("Unable to parse JSON from message {}", message.getMessageId());
						e.printStackTrace();

						// it's not likely that this message will be received correctly the next time
						messagesToDelete.add(message.getReceiptHandle());
						continue;
					}

					Consumer<ResultEnvelope> callback = callbacks.get(env.id);
					if (callback != null) {
						// callback could be null if the frontend was restarted
						callback.accept(env);
						callbacks.remove(env.id);
					}

					messagesToDelete.add(message.getReceiptHandle());
				}
			}
		}
	}
}
