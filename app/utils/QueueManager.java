package utils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.*;
import com.beust.jcommander.internal.Lists;
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
import org.opentripplanner.routing.core.TraverseModeSet;
import play.Logger;
import play.Play;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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

	static {
		objectMapper.registerModule(new JodaModule());
		objectMapper.registerModule(new TraverseModeSetModule());
	}

	/** S3 bucket in which results are placed */
	private final String outputLoc = Play.application().configuration().getString("cluster.results-bucket");

	private final ConcurrentMap<String, Consumer<ResultEnvelope>> callbacks = new ConcurrentHashMap<>();

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
		else
			queueUrl = getOrCreateJobQueue(req.graphId, req.jobId);

		// store the callback
		if (callback != null)
			callbacks.put(req.jobId + "_" + req.id, callback);

		//req.outputQueue = outputQueue;
		//req.outputLocation = outputLoc;
		req.directOutputUrl = Play.application().configuration().getString("cluster.url-internal") + "/api/result?key=" + key;

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

	private String getOrCreateJobQueue(String graphId, String jobId) {
		String queueName = prefix + "_" + graphId + "_" + jobId;
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
}
