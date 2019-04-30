package org.octri.hpoonfhir.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.octri.hpoonfhir.service.FhirService;
import org.octri.hpoonfhir.service.FhirServiceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "fhir-server-configuration")
@Configuration
public class FhirConfig {

	private static final Logger logger = LogManager.getLogger();

	private String name;
	private String url;
	private String version;
	private String authorize;
	private String token;
	private String redirect;
	private String clientId;
	// While this class is not Serializable, declaring this transient will protect against accidental exposure if it becomes so.
	private transient String clientSecret;
	private Boolean enableLogging;

	public FhirConfig() {

	}

	public FhirConfig(String name, String url, String version, String authorize, String token, String redirect, String clientId, String clientSecret, Boolean enableLogging) {
		this.name = name;
		this.url = url;
		this.version = version;
		this.authorize = authorize;
		this.token = token;
		this.redirect = redirect;
		this.clientId = clientId;
		setClientSecret(clientSecret);
		this.setEnableLogging(enableLogging);
		logger.warn("Application will authenticate with " + url);

	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	
	public String getAuthorize() {
		return authorize;
	}

	
	public void setAuthorize(String authorize) {
		this.authorize = authorize;
	}

	
	public String getToken() {
		return token;
	}

	
	public void setToken(String token) {
		this.token = token;
	}

	
	public String getRedirect() {
		return redirect;
	}

	
	public void setRedirect(String redirect) {
		this.redirect = redirect;
	}

	
	public String getClientId() {
		return clientId;
	}

	
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	
	public String getClientSecret() {
		return clientSecret;
	}

	
	public void setClientSecret(String clientSecret) {
		if (StringUtils.isBlank(clientSecret) || clientSecret.equals("none")) {
			logger.info("Configuring the FHIR server without a client secret.");
			this.clientSecret = null;
		} else {
			this.clientSecret = clientSecret;
		}
	}
	
	public Boolean getEnableLogging() {
		return enableLogging;
	}

	public void setEnableLogging(Boolean enableLogging) {
		this.enableLogging = enableLogging;
	}

	/**
	 * Construct the FhirService from configuration
	 * @return
	 */
	@Bean
	public FhirService fhirService() {
		return FhirServiceBuilder.build(this);
	}

}
