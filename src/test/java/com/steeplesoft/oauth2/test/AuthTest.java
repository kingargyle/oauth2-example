package com.steeplesoft.oauth2.test;

import com.steeplesoft.oauth2.Common;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import static org.junit.Assert.assertNotNull;
import org.testng.annotations.Test;

/**
 *
 * @author jdlee
 */
public class AuthTest extends Arquillian {

    @ArquillianResource
    private URL url;
    private Client client = JerseyClientBuilder.newClient();

    @Deployment(testable=false)
    public static WebArchive createDeployment() {
        WebArchive archive = ShrinkWrap.create(WebArchive.class)
                .addPackages(true, "com.steeplesoft.oauth2")
                .addAsWebInfResource(new FileAsset(new File("src/main/webapp/WEB-INF/beans.xml")), "beans.xml")
                .addAsWebInfResource(new FileAsset(new File("src/main/webapp/WEB-INF/web.xml")), "web.xml")
                .addAsLibraries(Maven.resolver().loadPomFromFile("pom.xml")
                    .importRuntimeDependencies().resolve().withTransitivity().asFile());
        return archive;
    }

    @Test
    public void authorizationRequest() {
        try {
            Response response = makeAuthCodeRequest();
            Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

            String authCode = getAuthCode(response);
            Assert.assertNotNull(authCode);
        } catch (OAuthSystemException | URISyntaxException | JSONException ex) {
            Logger.getLogger(AuthTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Test
    public void authCodeTokenRequest() throws OAuthSystemException {
        try {
            Response response = makeAuthCodeRequest();
            Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

            String authCode = getAuthCode(response);
            Assert.assertNotNull(authCode);
            OAuthAccessTokenResponse oauthResponse = makeTokenRequestWithAuthCode(authCode);
            assertNotNull(oauthResponse.getAccessToken());
            assertNotNull(oauthResponse.getExpiresIn());
        } catch (OAuthSystemException | URISyntaxException | JSONException | OAuthProblemException ex) {
            Logger.getLogger(AuthTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Test
    public void directTokenRequest() {
        try {
            OAuthClientRequest request = OAuthClientRequest
                    .tokenLocation(url.toString() + "api/token")
                    .setGrantType(GrantType.PASSWORD)
                    .setClientId(Common.CLIENT_ID)
                    .setClientSecret(Common.CLIENT_SECRET)
                    .setUsername(Common.USERNAME)
                    .setPassword(Common.PASSWORD)
                    .buildBodyMessage();

            OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
            OAuthAccessTokenResponse oauthResponse = oAuthClient.accessToken(request);
            assertNotNull(oauthResponse.getAccessToken());
            assertNotNull(oauthResponse.getExpiresIn());
        } catch (OAuthSystemException | OAuthProblemException ex ) {
            Logger.getLogger(AuthTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Test
    public void endToEndWithAuthCode() {
        try {
            Response response = makeAuthCodeRequest();
            Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

            String authCode = getAuthCode(response);
            Assert.assertNotNull(authCode);
            
            OAuthAccessTokenResponse oauthResponse = makeTokenRequestWithAuthCode(authCode);
            String accessToken = oauthResponse.getAccessToken();
            
            URL restUrl = new URL(url.toString() + "api/resource");
            WebTarget target = client.target(restUrl.toURI());
            String entity = target.request(MediaType.TEXT_HTML)
                    .header(Common.HEADER_AUTHORIZATION, "Bearer " + accessToken)
                    .get(String.class);
            System.out.println("Response = " + entity);
        } catch (MalformedURLException | URISyntaxException | OAuthProblemException | OAuthSystemException | JSONException ex) {
            Logger.getLogger(AuthTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void testValidTokenResponse(HttpURLConnection httpURLConnection) throws Exception {
        InputStream inputStream;
        if (httpURLConnection.getResponseCode() == 400) {
            inputStream = httpURLConnection.getErrorStream();
        } else {
            inputStream = httpURLConnection.getInputStream();
        }
        String responseBody = OAuthUtils.saveStreamAsString(inputStream);
        assert (Common.ACCESS_TOKEN_VALID.equals(responseBody));
    }

    private Response makeAuthCodeRequest() throws OAuthSystemException, URISyntaxException {
        OAuthClientRequest request = OAuthClientRequest
                .authorizationLocation(url.toString() + "api/authz")
                .setClientId(Common.CLIENT_ID)
                .setRedirectURI(url.toString() + "api/redirect")
                .setResponseType(ResponseType.CODE.toString())
                .setState("state")
                .buildQueryMessage();
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

    private OAuthAccessTokenResponse makeTokenRequestWithAuthCode(String authCode) throws OAuthProblemException, OAuthSystemException {
        OAuthClientRequest request = OAuthClientRequest
                .tokenLocation(url.toString() + "api/token")
                .setClientId(Common.CLIENT_ID)
                .setClientSecret(Common.CLIENT_SECRET)
                .setGrantType(GrantType.AUTHORIZATION_CODE)
                .setCode(authCode)
                .setRedirectURI(url.toString() + "api/redirect")
                .buildBodyMessage();
        OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
        OAuthAccessTokenResponse oauthResponse = oAuthClient.accessToken(request);
        return oauthResponse;
    }
}
