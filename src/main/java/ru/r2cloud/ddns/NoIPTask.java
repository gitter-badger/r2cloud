package ru.r2cloud.ddns;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import ru.r2cloud.uitl.Configuration;
import ru.r2cloud.uitl.Util;

public class NoIPTask implements Runnable {

	private static final Logger LOG = Logger.getLogger(NoIPTask.class.getName());
	private static final long RETRY_TIMEOUT = TimeUnit.MINUTES.toMillis(30);

	private final Configuration config;
	private final String username;
	private final String password;
	private final String domainName;

	private String currentExternalIp;
	private boolean fatalError = false;
	private Long retryAfter = null;

	public NoIPTask(Configuration config) throws Exception {
		this.config = config;
		username = getAndValidate(config, "ddns.noip.username");
		password = getAndValidate(config, "ddns.noip.password");
		domainName = getAndValidate(config, "ddns.noip.domain");
		currentExternalIp = config.getProperty("ddns.ip");
		retryAfter = config.getLong("ddns.retry.after.millis");
	}

	@Override
	public void run() {
		if (fatalError) {
			return;
		}
		// skip update due to ddns server error.
		// see the protocol at https://www.noip.com/integrate/response
		if (retryAfter != null) {
			if (retryAfter > System.currentTimeMillis()) {
				return;
			} else {
				config.remove("ddns.retry.after.millis");
				config.update();
				retryAfter = null;
			}
		}

		String externalIp = ExternalIpUtil.getExternalIp();
		if (currentExternalIp != null && currentExternalIp.equals(externalIp)) {
			return;
		}
		try {
			URL obj = new URL("https://dynupdate.no-ip.com/nic/update?hostname=" + domainName);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "r2cloud/0.1 info@r2cloud.ru");
			con.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.US_ASCII)));
			int responseCode = con.getResponseCode();
			if (responseCode != 200) {
				LOG.log(Level.SEVERE, "unable to update ddns. response code: " + responseCode + ". See logs for details");
				Util.toLog(LOG, con.getInputStream());
			} else {
				try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
					String response = in.readLine();
					if (response.startsWith("good") || response.startsWith("nochg")) {
						int index = response.indexOf(' ');
						if (index != -1) {
							currentExternalIp = response.substring(index + 1);
							config.setProperty("ddns.ip", currentExternalIp);
							config.update();
						}
					} else if (response.equals("nohost") || response.equals("badauth") || response.equals("badagent") || response.equals("!donator") || response.equals("abuse")) {
						LOG.log(Level.SEVERE, "fatal error detected: " + response + ". Please check ddns settings");
						fatalError = true;
					} else if (response.equals("911")) {
						LOG.log(Level.SEVERE, "ddns provider returned internal server error. Will retry update after " + RETRY_TIMEOUT + " millis");
						retryAfter = System.currentTimeMillis() + RETRY_TIMEOUT;
						// save it to show in UI
						config.setProperty("ddns.retry.after.millis", retryAfter);
						config.update();
					} else {
						LOG.log(Level.FINE, "unknown response code: " + response);
					}
				}
			}
			con.disconnect();
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "unable to update ddns", e);
		}
	}

	private static String getAndValidate(Configuration config, String name) throws Exception {
		String username = config.getProperty(name);
		if (username == null || username.trim().length() == 0) {
			throw new Exception(name + " cannot be empty");
		}
		return username;
	}

}
