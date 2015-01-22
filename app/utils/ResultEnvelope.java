package utils;

import java.io.Serializable;

import org.opentripplanner.analyst.ResultSet;

import com.conveyal.otpac.message.WorkResult;

/**
 * This is a class that stores several result sets: an upper bound, a lower bound, and a central tendency.
 * 
 * @author mattwigway
 */
public class ResultEnvelope implements Serializable {
	/**
	 * The best case/upper bound (e.g. number of jobs reachable under best-case travel time) 
	 */
	public ResultSet bestCase;
	
	/**
	 * The lower bound (e.g. number of jobs reachable under worst-case travel time)
	 */
	public ResultSet worstCase;

	/**
	 * The average case
	 */
	public ResultSet avgCase;
	
	/**
	 * The point estimate of the accessibility. If profile = false, this is the journey
	 * time returned by OTP. If profile = true, this will eventually be the central
	 * tendency.
	 */
	public ResultSet pointEstimate;
	
	/**
	 * The spread of results (future use). This could be the standard deviation or
	 * interquartile range, for example. 
	 */
	public ResultSet spread;
	
	/**
	 * Is this a profile request?
	 * If so, upperBound and lowerBound will be defined, and pointEstimate not.
	 * Otherwise, only pointEstimate will be defined.
	 */
	public boolean profile;

	/** The ID of the feature from whence this result envelope came */
	public String id;
	
	/**
	 * Build a result envelope from a WorkResult from OTPA Cluster.
	 */
	public ResultEnvelope (WorkResult res) {
		if (res.profile) {
			this.profile = true;
			this.bestCase = res.getBestCase();
			this.worstCase = res.getWorstCase();
			this.avgCase = res.getAvgCase();
			this.pointEstimate = null;
			this.spread = null;
			// use the point ID in case the work result failed.
			// note that point ID's may be null in general, but not in Analyst Server
			// because of how we generate pointsets from shapefiles.
			this.id = res.point.getId();
		}
		else {
			this.profile = false;
			this.pointEstimate = res.getResult();
			this.bestCase = null;
			this.worstCase = null;
			this.spread = null;
			this.id = res.point.getId()
		}
	}
	
	/**
	 * Build an empty result envelope.
	 */
	public ResultEnvelope () {
		// do nothing, restore default constructor
	}
	
	public static enum Which {
		BEST_CASE, WORST_CASE, POINT_ESTIMATE, SPREAD, AVERAGE
	}
}
