package controllers;

import models.Project;
import models.User;
import play.Play;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.*;

import java.io.IOException;

public class Application extends Controller {
	public static final String tmpPath = Play.application().configuration().getString("application.tmp");
	public static final String dataPath = Play.application().configuration().getString("application.data");
	public static final String binPath = Play.application().configuration().getString("application.bin");

	final static jsmessages.JsMessages messages = jsmessages.JsMessages.create(play.Play.application());

	@Security.Authenticated(Secured.class)
	public static Result index() throws IOException  {
		return ok(index.render());
    }

	public static Result tutorial() throws IOException  {

		String username = session().get("username");
		return ok(tutorial.render(username));
	}

	public static Result login() throws IOException  {
		return ok(login.render());
    }

	public static Result doLogin() throws IOException  {

		String username = request().body().asFormUrlEncoded().get("username")[0];
		String password = request().body().asFormUrlEncoded().get("password")[0];

		User user = User.getUserByUsername(username);

		if(user == null)
			return notFound();

		if(user.checkPassword(password)) {
			session().put("username", user.username);
			return ok();
		}
		else {
			return unauthorized();
		}

	}

	public static Result createUserForm() {
		return ok(create.render());
	}

	public static Result createUser() {

		String username = request().body().asFormUrlEncoded().get("username")[0];
		String password = request().body().asFormUrlEncoded().get("password")[0];
		String email = request().body().asFormUrlEncoded().get("email")[0];

		User u;

		// hard coding demo user creation with id for DF project
		try {

			if(User.getUserByUsername(username) != null)
				username = username + "_1";

			u = new User(username, password, email);
			u.addReadOnlyProjectPermission("db7c31708ec68280a1a94a8ca633dae1");
			u.save();

			session().put("username", u.username);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return redirect("/#db7c31708ec68280a1a94a8ca633dae1/-99.10929679870605/19.42223967548736/12/analysis-single/");
	}

	public static Result setPassword() {

		String userId = request().body().asFormUrlEncoded().get("userId")[0];
		String password = request().body().asFormUrlEncoded().get("password")[0];

		User u = User.getUser(userId);
		try {
			u.passwordHash = User.getPasswordHash(password);
			u.save();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ok();
	}

	public static Result linkUserProject(String username, String projectId) {

		User u = User.getUserByUsername(username);
		u.addProjectPermission(projectId);
		u.save();
		return ok();
	}
	
	public static Result createDemoProject() {
		
		if(Project.getProject("db7c31708ec68280a1a94a8ca633dae1") == null) {
			Project demoProject = new Project();
			demoProject.id = "db7c31708ec68280a1a94a8ca633dae1";
			demoProject.name = "DF Demo";
			demoProject.defaultLat = -99.1092967987060;
			demoProject.defaultLon = 19.42223967;
			demoProject.defaultZoom = 12;
				
			demoProject.save();
		}
		
		return ok();
	}

	public static Result logout() throws IOException  {
		session().clear();
		return redirect(routes.Application.login());
    }

	public static Result jsMessages() {
	    return ok(messages.generate("window.Messages"));
	}
}
