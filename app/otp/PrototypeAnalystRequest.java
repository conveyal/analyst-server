
package otp;

import java.util.TimeZone;

import controllers.Api;

public class PrototypeAnalystRequest extends AnalystRequest {
	
	// TODO move defaults to config
	private String date = "2014-06-09";
    private String time = "8:00 AM";
    private TimeZone timeZone = TimeZone.getTimeZone("America/Mexico_City");

    public PrototypeAnalystRequest() {
    	
		 this.maxWalkDistance = 40000;
		 this.clampInitialWait = 1800;
		 this.modes.setWalk(true);

		 this.arriveBy = false;
		 this.setDateTime(date, time, timeZone);
		 this.batch = true;
		 this.worstTime = this.dateTime + (this.arriveBy ? - (Api.maxTimeLimit + this.clampInitialWait): (Api.maxTimeLimit + this.clampInitialWait));
    }
    
}
