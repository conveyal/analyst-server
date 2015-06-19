package com.conveyal.analyst.server.controllers;

import models.Project;
import models.User;
import spark.Request;
import spark.Response;

import java.io.IOException;

import static spark.Spark.halt;

public class Application extends Controller {
	public static String doLogin(Request request, Response response) throws IOException  {

		String username = (String) request.attribute("username");
		String password = (String) request.attribute("password");

		User user = User.getUserByUsername(username);

		if(user == null) {
			response.status(NOT_FOUND);
			return "no such user";
		}

		if(user.checkPassword(password) && Boolean.TRUE.equals(user.active)) {
			request.session().attribute("username", user.username);
			return "welcome " + user.username + "!";
		}
		else {
			response.status(UNAUTHORIZED);
			return "bad username or password";
		}
	}

	public static String createUser(Request request, Response response) {

		String username = (String) request.attribute("username");
		String password = (String) request.attribute("password");
		String email = (String) request.attribute("email");

		User u;

		// hard coding demo user creation with id for DF project
		try {

			if(User.getUserByUsername(username) != null)
				username = username + "_1";

			u = new User(username, password, email);
			u.addReadOnlyProjectPermission("db7c31708ec68280a1a94a8ca633dae1");
			u.save();

			request.session().attribute("username", u.username);
			return "welcome " + u.username + "!";
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		response.redirect("/#db7c31708ec68280a1a94a8ca633dae1/-99.10929679870605/19.42223967548736/12/analysis-single/");
		return "welcome";
	}

	public static String setPassword(Request request, Response response) {

		String userId = (String) request.attribute("userId");
		String password = (String) request.attribute("password");

		User u = User.getUser(userId);

		if (!u.username.equals(request.session().attribute("username")) && !Boolean.TRUE.equals(u.admin))
			halt(UNAUTHORIZED, "cannot reset other user's password");

		try {
			u.passwordHash = User.getPasswordHash(password);
			u.save();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "password reset";
	}
	
	public static String createDemoProject(Request request, Response response) {
		
		if(Project.getProject("db7c31708ec68280a1a94a8ca633dae1") == null) {
			Project demoProject = new Project();
			demoProject.id = "db7c31708ec68280a1a94a8ca633dae1";
			demoProject.name = "DF Demo";
			demoProject.defaultLat = -99.1092967987060;
			demoProject.defaultLon = 19.42223967;
			demoProject.defaultZoom = 12;
				
			demoProject.save();
		}
		
		return "demo project created";
	}

	public static String logout(Request request, Response response)  {
		request.session().removeAttribute("username");
		return "good night";
    }
}
