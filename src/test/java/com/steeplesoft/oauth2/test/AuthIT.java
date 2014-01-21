package com.steeplesoft.oauth2.test;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import junit.framework.Assert;

import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.message.types.ResponseType;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.TagLibConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.steeplesoft.oauth2.Common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 
 * @author jdlee
 * @author dcarver - Enhanced to use Embedded Jetty Server
 */
public class AuthIT {

	private static URL url;
	private Client client = JerseyClientBuilder.newClient();
	private static Server server;

	@BeforeClass
	public static void startJettyServer() throws Exception {
		System.setProperty("DEBUG", "true");
		url = new URL("http://localhost:9080/");
		server = new Server(9080);

        WebAppContext context = new WebAppContext();
        String wardir = "target/oauth2-1.0-SNAPSHOT";
        context.setResourceBase(wardir);
        context.setDescriptor(wardir + "WEB-INF/web.xml");
        context.setConfigurations(new Configuration[] {
                        new AnnotationConfiguration(), new WebXmlConfiguration(),
                        new WebInfConfiguration(), new TagLibConfiguration(),
                        new PlusConfiguration(), new MetaInfConfiguration(),
                        new FragmentConfiguration(), new EnvConfiguration() });

        context.setContextPath("/");
        context.setParentLoaderPriority(true);
        
        server.setHandler(context);
        
		server.start();
		server.dump(System.err);
	}

	@AfterClass
	public static void stopServer() throws Exception {
		server.stop();
	}
	
	@Test
	public void testRootIndex() throws Exception {
		URL testUrl = new URL(url.toString() + "index.html");
		HttpURLConnection connection = (HttpURLConnection) testUrl.openConnection();
		connection.connect();
		
		int responseCode = connection.getResponseCode();
		assertEquals(200, responseCode);
	}

	@Test
	public void authorizationRequest() throws Exception {
			Response response = makeAuthCodeRequest();
			Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

			String authCode = getAuthCode(response);
			Assert.assertNotNull(authCode);
	}

	@Test
	public void authCodeTokenRequest() throws Exception {
			Response response = makeAuthCodeRequest();
			Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

			String authCode = getAuthCode(response);
			Assert.assertNotNull(authCode);
			OAuthAccessTokenResponse oauthResponse = makeTokenRequestWithAuthCode(authCode);
			assertNotNull(oauthResponse.getAccessToken());
			assertNotNull(oauthResponse.getExpiresIn());
	}

	@Test
	public void directTokenRequest() throws Exception {
			OAuthClientRequest request = OAuthClientRequest
					.tokenLocation(url.toString() + "api/token")
					.setGrantType(GrantType.PASSWORD)
					.setClientId(Common.CLIENT_ID)
					.setClientSecret(Common.CLIENT_SECRET)
					.setUsername(Common.USERNAME).setPassword(Common.PASSWORD)
					.buildBodyMessage();

			OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
			OAuthAccessTokenResponse oauthResponse = oAuthClient
					.accessToken(request);
			assertNotNull(oauthResponse.getAccessToken());
			assertNotNull(oauthResponse.getExpiresIn());
	}

	@Test
	public void endToEndWithAuthCode() throws Exception {
			Response response = makeAuthCodeRequest();
			Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

			String authCode = getAuthCode(response);
			Assert.assertNotNull(authCode);

			OAuthAccessTokenResponse oauthResponse = makeTokenRequestWithAuthCode(authCode);
			String accessToken = oauthResponse.getAccessToken();

			URL restUrl = new URL(url.toString() + "api/resource");
			WebTarget target = client.target(restUrl.toURI());
			String entity = target
					.request(MediaType.TEXT_HTML)
					.header(Common.HEADER_AUTHORIZATION,
							"Bearer " + accessToken).get(String.class);
			System.out.println("Response = " + entity);
	}

	void testValidTokenResponse(HttpURLConnection httpURLConnection)
			throws Exception {
		InputStream inputStream;
		if (httpURLConnection.getResponseCode() == 400) {
			inputStream = httpURLConnection.getErrorStream();
		} else {
			inputStream = httpURLConnection.getInputStream();
		}
		String responseBody = OAuthUtils.saveStreamAsString(inputStream);
		assert (Common.ACCESS_TOKEN_VALID.equals(responseBody));
	}

	private Response makeAuthCodeRequest() throws OAuthSystemException,
			URISyntaxException {
		OAuthClientRequest request = OAuthClientRequest
				.authorizationLocation(url.toString() + "api/authz")
				.setClientId(Common.CLIENT_ID)
				.setRedirectURI(url.toString() + "api/redirect")
				.setResponseType(ResponseType.CODE.toString())
				.setState("state").buildQueryMessage();
		WebTarget target = client.target(new URI(request.getLocationUri()));
		Response response = target.request(MediaType.TEXT_HTML).get();
		return response;
	}

	private String getAuthCode(Response response) throws JSONException {
		JSONObject obj = new JSONObject(response.readEntity(String.class));
		JSONObject qp = obj.getJSONObject("queryParameters");
		String authCode = null;
		if (qp != null) {
			authCode = qp.getString("code");
		}

		return authCode;
	}

	private OAuthAccessTokenResponse makeTokenRequestWithAuthCode(
			String authCode) throws OAuthProblemException, OAuthSystemException {
		OAuthClientRequest request = OAuthClientRequest
				.tokenLocation(url.toString() + "api/token")
				.setClientId(Common.CLIENT_ID)
				.setClientSecret(Common.CLIENT_SECRET)
				.setGrantType(GrantType.AUTHORIZATION_CODE).setCode(authCode)
				.setRedirectURI(url.toString() + "api/redirect")
				.buildBodyMessage();
		OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
		OAuthAccessTokenResponse oauthResponse = oAuthClient
				.accessToken(request);
		return oauthResponse;
	}
}
