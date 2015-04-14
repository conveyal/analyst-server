package otp;

import com.conveyal.otpac.PrototypeAnalystProfileRequest;
import com.conveyal.otpac.PrototypeAnalystRequest;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.opentripplanner.analyst.core.Sample;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.profile.Option;
import org.opentripplanner.profile.ProfileRequest;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.api.parameter.QualifiedMode;
import org.opentripplanner.api.parameter.QualifiedModeSet;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.python.google.common.collect.Collections2;

import play.Logger;

import java.io.File;
import java.io.IOException;
import java.util.TimeZone;


public class Analyst { 	
	public Analyst() {
		
	}
	 
	/**
	 * Build a routing request. Note that this does not set the routing context, you'll have to do that
	 * manually; this is because, in cluster mode, we don't want to serialize the routing context and send
	 * it over the wire.
	 */
	
	public RoutingRequest buildRequest(String graphId, LocalDate date, int time, GenericLocation latLon, String mode, int cutoffMinutes, DateTimeZone tz) {		
		PrototypeAnalystRequest req = new PrototypeAnalystRequest();
		
		req.dateTime = date.toDateTimeAtStartOfDay(tz).toDate().getTime() / 1000;
		req.dateTime += time;
		req.modes = new TraverseModeSet(mode);
		req.routerId = graphId;
		req.from = latLon;
		req.dominanceFunction = new DominanceFunction.EarliestArrival();
		req.worstTime = req.dateTime + cutoffMinutes * 60;
		
		// initial wait clamping is algorithmically invalid.
		req.clampInitialWait = 0;
		
		if (req.modes.isTransit()) {
			Logger.warn("Building a non-profile transit routing request, this probably shouldn't be happening.");
			req.walkReluctance = 1.0;
		}

		return req;
	}
	
	public ProfileRequest buildProfileRequest(String mode, LocalDate date, int fromTime, int toTime, double lat, double lon) {
		ProfileRequest req = new PrototypeAnalystProfileRequest();
		
		// split the modeset into two modes, using the logic in TraverseModeSet
                TraverseModeSet modes = new TraverseModeSet(mode);
		modes.setTransit(false);

		TraverseModeSet transitModes = new TraverseModeSet(mode);
		transitModes.setBicycle(false);
		transitModes.setCar(false);
		transitModes.setWalk(false);

        QualifiedModeSet qModes = new QualifiedModeSet("WALK");
        qModes.qModes.clear();
        for (TraverseMode tm : modes.getModes()) {
        	QualifiedMode qMode = new QualifiedMode(tm.toString());
        	qModes.qModes.add(qMode);
        }

		req.accessModes = req.egressModes = req.directModes = qModes;
		req.transitModes = transitModes;

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
		
        req.maxWalkTime = 20;
		req.maxBikeTime = 20;
		req.maxCarTime  = 20;
		req.minBikeTime = 5;
		req.minCarTime  = 5;
		
		req.limit       = 10;
		req.suboptimalMinutes = 5;
		
		// doesn't matter for analyst requests
		req.orderBy = Option.SortOrder.AVG;
		
		return req;
	}	
}
