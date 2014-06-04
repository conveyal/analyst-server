package controllers;

import java.util.concurrent.ConcurrentNavigableMap;

import models.Project;

import org.mapdb.DBMaker;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import play.*;
import play.mvc.*;
import views.html.*;

public class Application extends Controller {

	public static Result index()  {
		
    	return ok(index.render());
    }
	
	final static jsmessages.JsMessages messages = jsmessages.JsMessages.create(play.Play.application());

	public static Result jsMessages() {
	    return ok(messages.generate("window.Messages"));
	}
}
