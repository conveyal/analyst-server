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
import com.stormpath.sdk.cache.Cache;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupList;
import com.stormpath.sdk.http.HttpMethod;
import com.stormpath.sdk.http.HttpRequests;
import com.stormpath.sdk.idsite.AccountResult;
import com.stormpath.sdk.idsite.IdSiteUrlBuilder;
import com.stormpath.sdk.oauth.AccessTokenResult;
import com.stormpath.sdk.oauth.OauthAuthenticationResult;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;
import models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import static spark.Spark.halt;

/** handle authentication */
public class Authentication extends Controller {
    private static final Logger LOG = LoggerFactory.getLogger(Authentication.class);

    private static Application stormpathApp;
    private static Client stormpathClient;

    static {
        File stormpathConfig = new File(AnalystMain.config.getProperty("auth.stormpath-config"));

        if (!stormpathConfig.exists())
            LOG.error("Stormpath configuration does not exist");

        ApiKey key = ApiKeys.builder().setFileLocation(stormpathConfig.getAbsolutePath()).build();
        stormpathClient = Clients.builder().setApiKey(key).build();
        Tenant tenant = stormpathClient.getCurrentTenant();
        ApplicationList apps = tenant.getApplications(Applications.where(Applications.name().eqIgnoreCase(AnalystMain.config.getProperty("auth.stormpath-name"))));
        stormpathApp = apps.iterator().next();
    }

    /** used when the stormpath ID site is turned off */
    public static String doLogin(Request request, Response response) throws IOException {
        String username = request.queryParams("username");
        String password = request.queryParams("password");

        // authenticate with stormpath
        AuthenticationRequest req = new UsernamePasswordRequest(username, password);

        Account account;
        try {
            AuthenticationResult res = stormpathApp.authenticateAccount(req);
            account = res.getAccount();
            clearCache(account);
        } catch (ResourceException e) {
            LOG.warn("Login attempt failed for user {}", username);
            halt(UNAUTHORIZED, "Invalid username or password");
        }

        request.session().attribute("username", username);

        return "welcome " + username;
    }

    public static Object handleLogin (Request req, Response res) {
        AccountResult ar = stormpathApp.newIdSiteCallbackHandler(req.raw()).getAccountResult();
        Account a = ar.getAccount();

        if (a == null) {
            req.session().removeAttribute("username");
            res.redirect("/login", MOVED_TEMPORARILY);
            return "";
        }

        clearCache(a);
        req.session().attribute("username", a.getUsername());

        // TODO track intent through login
        res.redirect("/", MOVED_TEMPORARILY);
        return null;
    }

    /** Redirect to the stormpath ID site */
    public static Object redirectToLoginSite (Request req, Response res) {
        if (!Boolean.parseBoolean(AnalystMain.config.getProperty("auth.use-stormpath-id-site"))) {
            res.redirect("/login.html", MOVED_TEMPORARILY);
            return null;
        }

        IdSiteUrlBuilder builder = stormpathApp.newIdSiteUrlBuilder();
        builder.setCallbackUri(AnalystMain.config.getProperty("application.site-url") + "/handleLogin");
        String url = builder.build();

        // http 302 found
        res.redirect(url, MOVED_TEMPORARILY);
        return null;
    }

    /**
     * hackily clear the Stormpath cache now so that the next currentUser request will get up-to-date data
     * we need this so that if a user has their quota updated, they can log out and back in and see
     * the updated quota.
     * @param account
     */
    private static void clearCache (Account account) {
        try {
            GroupList memberships = account.getGroups();

            // quotas are stored as custom data so clear out relevant bits of the custom data cache.
            Cache<String, Group> customDataCache = stormpathClient.getDataStore().getCacheManager()
                    .getCache("com.stormpath.sdk.directory.CustomData");


            for (Group g : memberships) {
                customDataCache.remove(g.getHref() + "/customData");
            }

            customDataCache.remove(account.getHref() + "/customData");
        } catch (Exception e) {
            LOG.error("Failed to clear caches on login", e);
        }
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

        else {
            User u = new User(accounts.iterator().next());
            if (u.groupName == null)
                LOG.warn("No group specified for user {}", u);

            return u;
        }
    }

    public static String logout(Request request, Response response)  {
        request.session().removeAttribute("username");

        if (Boolean.parseBoolean(AnalystMain.config.getProperty("auth.use-stormpath-id-site"))) {
            // log out of ID site
            String url = stormpathApp.newIdSiteUrlBuilder()
                    .forLogout()
                    .setCallbackUri(AnalystMain.config.getProperty("application.site-url") + "/login")
                    .build();

            response.redirect(url, MOVED_TEMPORARILY);
        }
        else {
            response.redirect("/login", MOVED_TEMPORARILY);
        }
        return "";
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
            res.redirect("/login", MOVED_TEMPORARILY);
        }
    }

    /** Ensure users are authenticated, enabling CORS on OAuth requests is enabled */
    public static void authenticatedOrCors (Request req, Response res) {
        // first check for an authorization header/query param
        Object request = null;
        if (req.queryParams("accessToken") != null) {
            // build a fake request with the authorization header added
            // Stormpath only supports auth through authorization headers, not through
            // query parameters, but you can't use an authorization header in a CORS request.
            // Ergo, we "translate" the query parameters into a fake Authorization header.
            Map<String, String[]> headers = req.headers().stream()
                    .collect(Collectors.toMap(h -> h, h -> new String [] { req.headers(h) }));

            headers.put("Authorization", new String[] { "Bearer " + req.queryParams("accessToken") });

            request = HttpRequests
                    .method(HttpMethod.fromName(req.requestMethod()))
                    .headers(headers)
                    .build();

        }
        else if (req.headers("Authorization") != null) {
            request = req.raw();
        }

        if (request != null) {
            // oauth is present
            OauthAuthenticationResult oauth;
            try {
                oauth = stormpathApp.authenticateOauthRequest(request)
                        .execute();
            } catch (ResourceException rex) {
                halt(UNAUTHORIZED, "unable to authenticate provided OAuth tokens");
                // unreachable, but the java compiler doesn't know that.
                return;
            }

            req.attribute("username", oauth.getAccount().getUsername());
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Headers", "Content-Type");
            return;
        }

        // fall back to username/password auth
        else
            authenticated(req, res);
    }

    /** Exchange OAuth access key, secret for temporary bearer authorization */
    public static String getBearerToken (Request req, Response res) {
        AccessTokenResult tkr;

        Object request;

        // Stormpath only supports client credentials in Authorization headers, which cannot be used
        // cross-origin. We thus also support keys in the url which we inject into a fake request.
        // This is safe if used over HTTPS.
        if (req.queryParams("key") != null && req.queryParams("secret") != null) {
            // build a fake request with the header added
            Map<String, String[]> headers = req.headers().stream()
                    .collect(Collectors.toMap(h -> h, h -> new String [] { req.headers(h) }));


            String basic = req.queryParams("key") + ":" + req.queryParams("secret");

            headers.put("Authorization", new String[] { "Basic " + Base64.getEncoder().encodeToString(basic.getBytes()) });

            request = HttpRequests
                    .method(HttpMethod.fromName(req.requestMethod()))
                    .headers(headers).parameters(req.queryMap().toMap())
                    .build();
        }
        else
            request = req.raw();

        try {
            tkr = (AccessTokenResult) stormpathApp.authenticateOauthRequest(request).execute();
        } catch (ResourceException rex) {
            halt(UNAUTHORIZED);
            return null;
        }

        res.type("application/json");
        res.header("Access-Control-Allow-Origin", "*");
        return tkr.getTokenResponse().toJson();
    }

    /** Add cors headers to a response */
    public static Object corsHeaders(Request request, Response response) {
        response.header("Access-Control-Allow-Origin", "*");
        return "";
    }

    /** Get all the groups for this application */
    static GroupList getAllGroups () {
        return stormpathApp.getGroups();
    }
}