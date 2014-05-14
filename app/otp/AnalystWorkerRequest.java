package otp;

import model.IndicatorItem;

import com.vividsolutions.jts.geom.Point;

public class AnalystWorkerRequest {
	
	String graphId;
	String indicatorId;
	IndicatorItem item;
	String mode;
	Integer timeLimit;
	
	String date;
	String time;
	String timeZone;
	
	Integer span;
	Integer nSamples;
	
}