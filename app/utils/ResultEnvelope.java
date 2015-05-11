package utils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

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
	
	/** The ID of the shapefile/pointset from whence this result envelope came */
	public String shapefile;
	
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
	
	public void put (Which key, ResultSet val) {
		switch (key) {
		case BEST_CASE:
			this.bestCase = val;
			break;
		case WORST_CASE:
			this.worstCase = val;
			break;
		case POINT_ESTIMATE:
			this.pointEstimate = val;
			break;
		case SPREAD:
			this.spread = val;
			break;
		case AVERAGE:
			this.avgCase = val;
			break;
		}
	}
	
	/**
	 * Explode this result envelope into a result envelope for each contained attribute
	 * We do this because we need to retrieve all of the values for a particular variable quickly, in order to display the map,
	 * and looping over 10GB of data to do this is not tractable.
	 */
	public Map<String, ResultEnvelope> explode () {
		Map<String, ResultEnvelope> exploded = new HashMap<String, ResultEnvelope>();
		
		// find a non-null resultset
		for (String attr : (pointEstimate != null ? pointEstimate : avgCase).histograms.keySet()) {
			ResultEnvelope env = new ResultEnvelope();
			
			for (Which which : Which.values()) {
				ResultSet orig = this.get(which);
				
				if (orig != null) {
					ResultSet rs = new ResultSet();
					rs.id = orig.id;
					rs.histograms.put(attr, orig.histograms.get(attr));
					env.put(which, rs);
					env.id = this.id;
				}
			}
			
			exploded.put(attr, env);
		}
		
		return exploded;
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
