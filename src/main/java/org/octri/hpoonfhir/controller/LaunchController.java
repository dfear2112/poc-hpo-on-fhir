package org.octri.hpoonfhir.controller;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.octri.hpoonfhir.controller.exception.AuthorizationFailedException;
import org.octri.hpoonfhir.domain.AccessTokenResponse;
import org.octri.hpoonfhir.domain.FhirSessionInfo;
import org.octri.hpoonfhir.service.FhirService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class LaunchController {

	private static final Logger logger = LogManager.getLogger();

	private static final String ISSUER_PARAMETER = "iss";
	private static final String LAUNCH_ID_PARAMETER = "launch";

	@Autowired
	private FhirService fhirService;

	@Autowired
	private FhirSessionInfo fhirSessionInfo;

	/**
	 * Get the launch request from the EHR and use it to initiate the authorization
	 * 
	 * @param request
	 * @param response
	 */
	@GetMapping("/launch")
	public void launch(HttpServletRequest request, HttpServletResponse response) {

		String launch = request.getParameter(LAUNCH_ID_PARAMETER);
		String serviceUri = request.getParameter(ISSUER_PARAMETER);
		Assert.isTrue(fhirService.getServiceEndpoint().equals(serviceUri),
			"This application is not configured to authenticate to the FHIR service " + serviceUri);
		Assert.isTrue(launch != null, "A launch parameter must be passed to initiate authorization.");

		// Escape the request parameters. FHIR does not proscribe any format for these parameters, but we
		// can safely assume they shouldn't have HTML in them.
		launch = StringEscapeUtils.escapeHtml4(launch);
		serviceUri = StringEscapeUtils.escapeHtml4(serviceUri);

		// From http://docs.smarthealthit.org/tutorials/authorization/ - Just a way of keeping track of state for
		// managing multiple launches
		// TODO: Should probably be saving the state and checking the auth response for a match
		String state = UUID.randomUUID().toString();
		fhirSessionInfo.setState(state);

		response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(fhirService.getAuthorizeEndpoint());
		stringBuilder.append("?client_id=");
		stringBuilder.append(fhirService.getClientId());
		stringBuilder.append("&response_type=");
		stringBuilder.append("code");
		stringBuilder.append("&scope=");
		stringBuilder.append("launch");
		stringBuilder.append("&launch=");
		stringBuilder.append(launch);
		stringBuilder.append("&redirect_uri=");
		stringBuilder.append(fhirService.getRedirectUri());
		stringBuilder.append("&aud=");
		stringBuilder.append(serviceUri);
		stringBuilder.append("&state=");
		stringBuilder.append(state);

		response.setHeader("Location", stringBuilder.toString());

	}

	/**
	 * The FHIR server will redirect here and provide a code after authorize step. If a patient context is provided,
	 * navigate to the patient. Otherwise, go to search.
	 * 
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	@GetMapping("/authorize")
	public void authorize(HttpServletRequest request, HttpServletResponse response) {

		try {
			// Extract and escape the state parameter and make sure it matches
			String state = request.getParameter("state");
			Assert.isTrue(state != null, "A state must be passed to complete authorization.");
			Assert.isTrue(fhirSessionInfo.hasState() && fhirSessionInfo.getState().equals(state),
				"The state does not match expectations.");

			// Extract and escape the code parameter
			String code = request.getParameter("code");
			Assert.isTrue(code != null, "A code must be passed to complete authorization.");
			code = StringEscapeUtils.escapeHtml4(code);

			// Get the token and store it in the session info for subsequent requests
			AccessTokenResponse tokenResponse = getToken(code);
			fhirSessionInfo.setToken(tokenResponse.getAccessToken());

			if (tokenResponse.getPatient() != null) {
				// Ensure no HTML in patient id
				String patient = StringEscapeUtils.escapeHtml4(tokenResponse.getPatient());
				response.sendRedirect(request.getContextPath() + "/patient/" + patient);
			} else {
				response.sendRedirect(request.getContextPath() + "/");
			}
		} catch (Exception e) {
			throw new AuthorizationFailedException();
		}
	}

	/**
	 * Exchange the code for a token and return the response
	 * 
	 * @param code
	 * @return
	 * @throws Exception
	 */
	private AccessTokenResponse getToken(String code) throws Exception {
		URL obj = null;

		try {
			obj = new URL(fhirService.getTokenEndpoint());
		} catch (MalformedURLException e) {
			logger.error("The token endpoint is misconfigured and cannot be converted to a URL");
			throw new RuntimeException("Error reading token endpoint");
		}
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();

		// Setting basic post request
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Language", "en-US");
		con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

		String postData = "code=" + code
			+ "&grant_type=authorization_code&redirect_uri=" + fhirService.getRedirectUri();

		if (fhirService.getClientSecret() != null) {
			// If there is a client secret, add an authorization
			String authHeader = String.format("%s:%s", fhirService.getClientId(), fhirService.getClientSecret());
			String encoded = new String(org.apache.commons.codec.binary.Base64.encodeBase64(authHeader.getBytes()));
			con.setRequestProperty("Authorization", String.format("Basic %s", encoded));
		} else {
			// If no client secret, pass the client id in the post
			postData += "&client_id=" + fhirService.getClientId();
		}

		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(postData);
		wr.flush();
		wr.close();

		int responseCode = con.getResponseCode();
		// TODO: Handle response code <> 200

		BufferedReader in = new BufferedReader(
			new InputStreamReader(con.getInputStream()));
		String output;
		StringBuffer response = new StringBuffer();

		while ((output = in.readLine()) != null) {
			response.append(output);
		}
		in.close();

		// Different servers may return additional parameters. Ignore them.
		ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		;
		try {
			AccessTokenResponse token = om.readValue(response.toString(), AccessTokenResponse.class);
			return token;
		} catch (Exception e) {
			logger.error("Could not deserialize token response from FHIR server.");
			throw new RuntimeException("Error reading token response");
		}
	}
}
