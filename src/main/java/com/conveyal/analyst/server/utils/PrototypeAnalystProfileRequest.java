package com.conveyal.analyst.server.utils;

import com.conveyal.r5.api.util.LegMode;
import com.conveyal.r5.api.util.TransitModes;
import com.conveyal.r5.profile.ProfileRequest;

import java.util.EnumSet;

/**
 * A profile request with reasonable defaults.
 * 
 * @author mattwigway
 */
public class PrototypeAnalystProfileRequest extends ProfileRequest {
	public PrototypeAnalystProfileRequest () {
		walkSpeed = 1.4f;
		bikeSpeed = 4.1f;
		carSpeed = 20f;
		
		streetTime = 90;
		maxWalkTime = 15;
		maxBikeTime = 45;
		maxCarTime = 45;
		minBikeTime = 10;
		minCarTime = 10;
		
		// doesn't matter for analyst requests
		// orderBy = Option.SortOrder.AVG;
		
		analyst = true;
		
		limit = 10;
		suboptimalMinutes = 5;
		accessModes = directModes = egressModes = EnumSet.of(LegMode.WALK);
		transitModes = EnumSet.allOf(TransitModes.class);
	}
}
