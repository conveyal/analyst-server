package model;

import java.util.TimeZone;
import controllers.Application;

public class PrototypeAnalystRequest extends AnalystRequest {
	
	// TODO move defaults to config
	private String date = "2014-04-25";
    private String time = "7:00 AM";
    private TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");

    public PrototypeAnalystRequest() {
    	
		 this.maxWalkDistance = 40000;
		 this.clampInitialWait = 1800;
		 this.modes.setWalk(true);
		
		 this.arriveBy = false;
		 this.setDateTime(date, time, timeZone);
		 this.batch = true;
		 this.worstTime = this.dateTime + (this.arriveBy ? - (Application.maxTimeLimit + this.clampInitialWait): (Application.maxTimeLimit + this.clampInitialWait));
    }
    
}
