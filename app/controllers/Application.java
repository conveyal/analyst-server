package controllers;

import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import otp.Analyst;
import play.*;
import play.mvc.*;
import views.html.*;

public class Application extends Controller {
	
	public static long maxTimeLimit = 3600;
	
	public static Analyst analyst = new Analyst();
	
	public static Result batch(String graphId, String indicatorId, Integer page, Integer pageCount, String mode, Integer timeLimit, String date, String time, String timeZone) throws NoSuchAuthorityCodeException, FactoryException {
	    	
    	Application.analyst.batch(graphId, indicatorId, page, pageCount, mode, timeLimit, date, time, timeZone);
		return ok();
    }
}
