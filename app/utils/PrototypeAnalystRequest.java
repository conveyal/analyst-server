package utils;

import org.opentripplanner.routing.core.RoutingRequest;

public class PrototypeAnalystRequest extends RoutingRequest {
	private static final long serialVersionUID = 1L;
	public static final int MAX_TIME = 7200;
	
	public PrototypeAnalystRequest() {
		this.maxWalkDistance = 40000;
		this.clampInitialWait = 1800;
		this.modes.setWalk(true);
		this.arriveBy = false;
		this.batch = true;
		this.worstTime = this.dateTime + (this.arriveBy ? - (MAX_TIME + this.clampInitialWait): (MAX_TIME + this.clampInitialWait));
	}
}