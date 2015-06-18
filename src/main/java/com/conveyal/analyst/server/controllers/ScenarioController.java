package com.conveyal.analyst.server.controllers;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import models.TransportScenario;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

import java.io.IOException;
import java.util.Collection;

@Security.Authenticated(Secured.class)
public class ScenarioController extends Controller {
	public static Result getById (String id) throws JsonMappingException, JsonGenerationException, IOException {
		TransportScenario s = TransportScenario.getScenario(id);
		
		if (s == null)
			return notFound();
		
		else return ok(Api.toJson(s, false)).as("application/json");
	}
	
	public static Result get (String projectId) throws JsonMappingException, JsonGenerationException, IOException {
		Collection<TransportScenario> s;
		
		if (projectId != null) {
			s = TransportScenario.getByProject(projectId);
		}
		else {
			s = TransportScenario.getAll(); 
		}
		
		return ok(Api.toJson(s, false)).as("application/json");
	}
	
	public static Result create () throws JsonParseException, JsonMappingException, IOException {
		TransportScenario s = Api.mapper.readValue(request().body().asJson().traverse(), TransportScenario.class);
		s.generateId();
		s.save();
		return ok(Api.toJson(s, false)).as("application/json");
	}
	
	public static Result update (String id) throws JsonParseException, JsonMappingException, IOException {
		TransportScenario s = Api.mapper.readValue(request().body().asJson().traverse(), TransportScenario.class);
		
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
		
		TransportScenario s = TransportScenario.getScenario(id);
		
		if (s == null)
			return notFound();
		
		s.delete();
		
		return ok();
	}
}
