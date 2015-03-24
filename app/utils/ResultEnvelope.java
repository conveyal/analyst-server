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
	 * time returned by OTP.
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
			this.avgCase = res.getPointEstimate();
			this.pointEstimate = null;
			this.spread = null;
			// the surface will never be null, because it is only created if the workresult was successful
			this.id = this.bestCase.id;
		}
		else {
			this.profile = false;
			this.pointEstimate = res.getPointEstimate();
			this.avgCase = null;
			this.bestCase = null;
			this.worstCase = null;
			this.spread = null;
			this.id = this.pointEstimate.id;
		}
	}
	
	public ResultSet get (Which key) {
		switch (key) {
		case BEST_CASE:
			return this.bestCase;
		case WORST_CASE:
			return this.worstCase;
		case POINT_ESTIMATE:
			return this.pointEstimate;
		case SPREAD:
			return this.spread;
		case AVERAGE:
			return this.avgCase;
		default:
			throw new IllegalStateException("Invalid result type!");
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
