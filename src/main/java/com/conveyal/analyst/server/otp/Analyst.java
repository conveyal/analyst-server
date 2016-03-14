package com.conveyal.analyst.server.otp;

import com.conveyal.r5.api.util.LegMode;
import com.conveyal.r5.api.util.TransitModes;
import com.conveyal.r5.profile.Mode;
import com.conveyal.r5.profile.ProfileRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

public class Analyst {
	private static final Logger LOG = LoggerFactory.getLogger(Analyst.class);

	public Analyst() {
		
	}

	public static ProfileRequest buildProfileRequest(String modes, LocalDate date, int fromTime, int toTime, double lat, double lon) {
		ProfileRequest req = new ProfileRequest();

		req.transitModes = EnumSet.noneOf(TransitModes.class);
		EnumSet<LegMode> modeSet = EnumSet.noneOf(LegMode.class);
		for (String mode : modes.split(",")) {
			if ("TRANSIT".equalsIgnoreCase(mode)) {
				req.transitModes = EnumSet.allOf(TransitModes.class);
			} else {
				modeSet.add(LegMode.valueOf(mode));
			}
		}
		req.accessModes = req.egressModes = req.directModes = modeSet;

        req.fromLat    = lat;
        req.fromLon    = lon;
        req.toLat	   = lat; // not used but required
        req.toLon      = lon;
        req.analyst	   = true;
        req.fromTime   = fromTime;
        req.toTime     = toTime;
        req.walkSpeed  = 1.4f;
        req.bikeSpeed  = 4.1f;
        req.carSpeed   = 20f;
        req.streetTime = 15;
        req.date       = date;
		
        // Set the max walk and bike times to large numbers so that small changes in the network will
        // not cause transit stops or destinations that were otherwise reachable by a walk to become
        // reachable only by a convoluted out-and-back transit trip.
        req.maxWalkTime = 20;
		req.maxBikeTime = 20;
		req.maxCarTime  = 20;
		req.minBikeTime = 5;
		req.minCarTime  = 5;
		
		req.limit       = 10;
		req.suboptimalMinutes = 5;
		
		// doesn't matter for analyst requests
		// req.orderBy = Option.SortOrder.AVG;
		
		return req;
	}	
}
