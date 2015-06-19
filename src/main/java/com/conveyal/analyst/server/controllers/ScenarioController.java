package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.utils.JsonUtil;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import models.Project;
import models.TransportScenario;
import models.User;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static spark.Spark.halt;

public class ScenarioController extends Controller {
	public static TransportScenario getById (Request req, Response res) throws JsonMappingException, JsonGenerationException, IOException {
		String id = req.params("id");
		TransportScenario s = TransportScenario.getScenario(id);

		if (s == null || !currentUser(req).hasReadPermission(s.projectId))
			halt(NOT_FOUND, "No such scenario or you do not have access to it");
		
		return s;
	}
	
	public static Collection<TransportScenario> get (Request req, Response res) throws JsonMappingException, JsonGenerationException, IOException {
		String projectId = req.queryParams("projectId");

		if (projectId != null && (Project.getProject(projectId) == null || !currentUser(req).hasReadPermission(projectId)))
			halt(NOT_FOUND);

		Collection<TransportScenario> s;
		
		if (projectId != null) {
			s = TransportScenario.getByProject(projectId);
		}
		else {
			Set<String> userProjects = currentUser(req).projectPermissions.stream()
					.filter(pp -> pp.read)
					.map(pp -> pp.projectId)
					.collect(Collectors.toSet());

			s = TransportScenario.getAll().stream()
					.filter(ts -> userProjects.contains(ts.projectId))
					.collect(Collectors.toList());
		}
		
		return s;
	}
	
	public static TransportScenario create (Request req, Response res) throws JsonParseException, JsonMappingException, IOException {
		TransportScenario s = JsonUtil.getObjectMapper().readValue(req.body(),
				TransportScenario.class);

		if (s.projectId == null)
			halt(BAD_REQUEST, "Please specify a project ID");

		if (Project.getProject(s.projectId) == null || !currentUser(req).hasWritePermission(s.projectId))
			halt(NOT_FOUND, "no such project or you do not have access to it");

		s.generateId();
		s.save();
		return s;
	}
	
	public static TransportScenario update (Request req, Response res) throws JsonParseException, JsonMappingException, IOException {
		TransportScenario s = JsonUtil.getObjectMapper().readValue(req.body(),
				TransportScenario.class);
		String id = req.params("id");

		if (s.id == null || !s.id.equals(id))
			halt(BAD_REQUEST, "please specify an ID in the JSON");
		
		if (!s.exists())
			halt(NOT_FOUND, "The scenario was not found");

		TransportScenario ex = TransportScenario.getScenario(s.id);
		User u = currentUser(req);

		if (s.projectId == null || Project.getProject(s.projectId) == null ||
				!u.hasWritePermission(s.projectId) ||
				!u.hasWritePermission(ex.projectId))
			halt(NOT_FOUND, "Project not found or you do not have write access to it");
		
		s.save();
		return s;
	}
	
	public static TransportScenario delete (Request req, Response res) {
		String id = req.params("id");

		if (id == null)
			halt(BAD_REQUEST, "specify an ID");

		TransportScenario s = TransportScenario.getScenario(id);
		
		if (s == null || !currentUser(req).hasWritePermission(s.projectId))
			halt(NOT_FOUND);
		
		s.delete();
		
		return s;
	}
}
