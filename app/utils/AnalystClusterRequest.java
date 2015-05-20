package utils;

import org.opentripplanner.analyst.PointFeature;

import java.io.Serializable;

/**
 * Marker interface for requests sent to an SPTWorker.
 * @author matthewc
 *
 */
public abstract class AnalystClusterRequest implements Serializable {
	/** The ID of the destinations pointset */
	public String destinationPointsetId;
	
	/** The ID of the graph against which to calculate this request */
	public String graphId;
	
	/** The origin */
	public PointFeature from;
	
	/** The job ID this is associated with */
	public String jobId;

	/** The id of this particular origin */
	public String id;

	/** How should this request be fulfilled? Should it be placed in S3 or enqueued in SQS? */
	public RequestDisposition disposition;

	/**
	 * Where should the result be delivered?
	 * For disposition = STORE, this is an S3 bucket, where files will be placed in the folder {jobId}/{id}.json
	 * For disposition = ENQUEUE, this is an SQS queue.
	 */
	public String outputLocation;
	
	/** Should times be included in the results (i.e. ResultSetWithTimes rather than ResultSet) */
	public boolean includeTimes = false;
	
	/** Is this a profile request? */
	public boolean profile;
	
	public AnalystClusterRequest(PointFeature from, String destinationPointsetId, String graphId, boolean profile) {
		this.from = from;
		this.destinationPointsetId = destinationPointsetId;
		this.graphId = graphId;
		this.profile = profile;
	}
	
	/** used for deserialization from JSON */
	public AnalystClusterRequest () { /* do nothing */ }

	/** How should a request be delivered to the client */
	public static enum RequestDisposition {
		/** Enqueue the result for immediate consumption by the client */
		ENQUEUE,
		/** Store the result for later consumption */
		STORE
	}
}
