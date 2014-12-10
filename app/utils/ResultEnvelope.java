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
	 * The upper bound (e.g. number of jobs reachable under best-case travel time) 
	 */
	public ResultSet upperBound;
	
	/**
	 * The lower bound (e.g. number of jobs reachable under worst-case travel time)
	 */
	public ResultSet lowerBound;
	
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
			this.upperBound = res.getBestCase();
			this.lowerBound = res.getWorstCase();
			this.pointEstimate = null;
			this.spread = null;
			this.id = this.upperBound.id;
		}
		else {
			this.profile = false;
			this.pointEstimate = res.getResult();
			this.upperBound = null;
			this.lowerBound = null;
			this.spread = null;
			this.id = this.pointEstimate.id;
		}
	}
	
	/**
	 * Build an empty result envelope.
	 */
	public ResultEnvelope () {
		// do nothing, restore default constructor
	}
	
	public static enum Which {
		UPPER_BOUND, LOWER_BOUND, POINT_ESTIMATE, SPREAD;
	}
}
