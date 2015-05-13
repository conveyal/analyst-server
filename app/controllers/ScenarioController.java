package controllers;

import java.io.IOException;
import java.util.Collection;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import models.Scenario;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

@Security.Authenticated(Secured.class)
public class ScenarioController extends Controller {
	public static Result getById (String id) throws JsonMappingException, JsonGenerationException, IOException {
		Scenario s = Scenario.getScenario(id);
		
		if (s == null)
			return notFound();
		
		else return ok(Api.toJson(s, false)).as("application/json");
	}
	
	public static Result get (String projectId) throws JsonMappingException, JsonGenerationException, IOException {
		Collection<Scenario> s;
		
		if (projectId != null) {
			s = Scenario.getByProject(projectId);
		}
		else {
			s = Scenario.getAll(); 
		}
		
		return ok(Api.toJson(s, false)).as("application/json");
	}
	
	public static Result create () throws JsonParseException, JsonMappingException, IOException {
		Scenario s = Api.mapper.readValue(request().body().asJson().traverse(), Scenario.class);
		s.generateId();
		s.save();
		return ok(Api.toJson(s, false)).as("application/json");
	}
	
	public static Result update (String id) throws JsonParseException, JsonMappingException, IOException {
		Scenario s = Api.mapper.readValue(request().body().asJson().traverse(), Scenario.class);
		
		if (s.id == null || !s.id.equals(id))
			return badRequest();
		
		if (!s.exists())
			return notFound();
		
		s.save();
		return ok(Api.toJson(s, false)).as("application/json");
	}
	
	public static Result delete (String id) {
		if (id == null)
			return badRequest();
		
		Scenario s = Scenario.getScenario(id);
		
		if (s == null)
			return notFound();
		
		s.delete();
		
		return ok();
	}
}
