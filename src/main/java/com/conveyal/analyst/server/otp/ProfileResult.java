package com.conveyal.analyst.server.otp;

import org.opentripplanner.analyst.TimeSurface;

public class ProfileResult {

		public Integer id;
		public TimeSurface min;
		public TimeSurface max;
		
		public ProfileResult(Integer id, TimeSurface min, TimeSurface max) {
			this.id = id;
			this.min = min;
			this.max = max;
		}
		
}
