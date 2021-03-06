package ru.r2cloud.web.controller;

import ru.r2cloud.web.AbstractHttpController;
import ru.r2cloud.web.Authenticator;
import ru.r2cloud.web.ModelAndView;
import ru.r2cloud.web.Redirect;
import ru.r2cloud.web.ValidationResult;
import ru.r2cloud.web.WebServer;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;

public class DoLogin extends AbstractHttpController {

	private final Authenticator auth;

	public DoLogin(Authenticator auth) {
		this.auth = auth;
	}

	@Override
	public ModelAndView doPost(IHTTPSession session) {
		String username = WebServer.getParameter(session, "j_username");
		String password = WebServer.getParameter(session, "j_password");
		return doLogin(auth, username, password);
	}

	public static ModelAndView doLogin(Authenticator auth, String username, String password) {
		String cookie = auth.authenticate(username, password);
		if (cookie == null) {
			ModelAndView result = new ModelAndView();
			result.setView("login");
			result.put("errors", new ValidationResult("Invalid login or password"));
			result.put("username", username);
			return result;
		}
		Redirect result = new Redirect("/admin/status");
		result.addHeader("Set-Cookie", "JSESSIONID=" + cookie + "; Max-Age=" + auth.getMaxAgeMillis() / 1000);
		return result;
	}

	@Override
	public String getRequestMappingURL() {
		return "/dologin";
	}

}
