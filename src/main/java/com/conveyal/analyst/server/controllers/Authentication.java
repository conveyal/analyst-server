package com.conveyal.analyst.server.controllers;

import com.conveyal.analyst.server.AnalystMain;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Accounts;
import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.api.ApiKeys;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.ApplicationList;
import com.stormpath.sdk.application.Applications;
import com.stormpath.sdk.authc.AuthenticationRequest;
import com.stormpath.sdk.authc.AuthenticationResult;
import com.stormpath.sdk.authc.UsernamePasswordRequest;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;
import models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.File;
import java.io.IOException;

import static spark.Spark.halt;

/** handle authentication */
public class Authentication extends Controller {
    private static final Logger LOG = LoggerFactory.getLogger(Authentication.class);

    private static Application stormpathApp;

    static {
        File stormpathConfig = new File(AnalystMain.config.getProperty("auth.stormpath-config"));

        if (!stormpathConfig.exists())
            LOG.error("Stormpath configuration does not exist");

        ApiKey key = ApiKeys.builder().setFileLocation(stormpathConfig.getAbsolutePath()).build();
        Client authClient = Clients.builder().setApiKey(key).build();
        Tenant tenant = authClient.getCurrentTenant();
        ApplicationList apps = tenant.getApplications(Applications.where(Applications.name().eqIgnoreCase(AnalystMain.config.getProperty("auth.stormpath-name"))));
        stormpathApp = apps.iterator().next();

    }

    public static String doLogin(Request request, Response response) throws IOException {
        String username = request.queryParams("username");
        String password = request.queryParams("password");

        // authenticate with stormpath
        AuthenticationRequest req = new UsernamePasswordRequest(username, password);

        Account account;
        try {
            AuthenticationResult res = stormpathApp.authenticateAccount(req);
            account = res.getAccount();
        } catch (ResourceException e) {
            LOG.warn("Login attempt failed for user {}", username);
            halt(UNAUTHORIZED, "Invalid username or password");
        }

        request.session().attribute("username", username);

        return "welcome " + username;
    }

    /** get the current user, for the client */
    public static User getCurrentUser (Request req, Response res) {
        return currentUser(req);
    }

    /** get the user object from Stormpath for a particular username */
    public static User getUser(String username) {
        if (username == null)
            return null;

        // TODO: use a cache here? let's see what latency is like.
        AccountList accounts = stormpathApp.getAccounts(Accounts.where(Accounts.username().eqIgnoreCase(username)));

        if (accounts.getSize() == 0)
            return null;

        else if (accounts.getSize() > 1) {
            LOG.error("Found multiple accounts for username {}", username);
            return null;
        }

        else
            return new User(accounts.iterator().next());
    }

    public static String logout(Request request, Response response)  {
        request.session().removeAttribute("username");
        response.redirect("/login.html", MOVED_TEMPORARILY);
        return null;
    }

    /** A before filter for routes to ensure users are authenticated */
    public static void authenticated (Request request, Response response) {
        String username = (String) request.session().attribute("username");
        if (currentUser(request) == null) {
            halt(UNAUTHORIZED, "you must log in to access this page");
        }
    }

    /** same as above but generates a redirect instead of a 401 Unauthorized */
    public static void uiAuthenticated (Request req, Response res) {
        String username = (String) req.session().attribute("username");
        if (currentUser(req) == null) {
            res.redirect("/login.html", MOVED_TEMPORARILY);
        }
    }

    /** Ensure users are authenticated or CORS is enabled */
    public static void authenticatedOrCors (Request req, Response res) {
        if (!Boolean.parseBoolean(AnalystMain.config.getProperty("api.allow-unauthenticated-access", "false"))) {
            if (currentUser(req) == null) {
                halt(UNAUTHORIZED, "you must log in to access this page");
            }
        }
        else {
            res.header("Access-Control-Allow-Origin", "*");
        }
    }
}