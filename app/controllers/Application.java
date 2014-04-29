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
	
	public static Result batch(String graphId, Integer page, Integer pageCount, String mode, Integer timeLimit) throws NoSuchAuthorityCodeException, FactoryException {
	    	
    	Application.analyst.batch(graphId, page, pageCount, mode, timeLimit);
		return ok();
    }
}
