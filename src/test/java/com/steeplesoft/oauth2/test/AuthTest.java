package com.steeplesoft.oauth2.test;

import com.steeplesoft.oauth2.Common;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.DependencyResolvers;
import org.jboss.shrinkwrap.resolver.api.maven.MavenDependencyResolver;
import org.testng.annotations.Test;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author jdlee
 */
@RunAsClient
public class AuthTest extends Arquillian {
    @ArquillianResource 
    private URL url;

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive archive = null;
        try {
            MavenDependencyResolver resolver = 
                    DependencyResolvers.use(MavenDependencyResolver.class).loadMetadataFromPom("pom.xml");

//            final PomEquippedResolveStage resolver = Maven.resolver().loadPomFromFile("pom.xml");
//            resolver.resolve().withTransitivity().asFile();

            archive = ShrinkWrap.create(WebArchive.class)
                    .addPackages(true, "com.steeplesoft.oauth2")
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                    .addAsWebInfResource(new FileAsset(new File("src/main/webapp/WEB-INF/web.xml")), "web.xml")
                    .addAsLibraries(resolver.artifacts(
                        "org.apache.oltu.oauth2:org.apache.oltu.oauth2.common",
                        "org.apache.oltu.oauth2:org.apache.oltu.oauth2.authzserver",
                        "org.apache.oltu.oauth2:org.apache.oltu.oauth2.resourceserver"
                    ).resolveAsFiles())
            ;
            System.out.println(archive.toString(true));
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
        return archive;
    }

    @Test
    public void testAuthz() {
        try {
            System.out.println("Test!");
            URL restUrl = new URL(url.toString() + "api/resource");
            System.out.println(restUrl.toString());
            Client client = JerseyClientBuilder.newClient();
            WebTarget target = client.target(restUrl.toURI());
            String response = target.request(MediaType.TEXT_HTML)
                    .header(Common.HEADER_AUTHORIZATION, Common.AUTHORIZATION_HEADER_OAUTH2)
                    .get(String.class);
            System.out.println("Response = " + response);
        } catch (MalformedURLException | URISyntaxException ex) {
            Logger.getLogger(AuthTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) {
        new AuthTest().run();
    }

    public void run() {
        try {
            URL url = new URL("http://localhost:8080/oauth2-1.0-SNAPSHOT/api/resource");
            URLConnection c = url.openConnection();
            c.addRequestProperty(Common.HEADER_AUTHORIZATION, Common.AUTHORIZATION_HEADER_OAUTH2);

            if (c instanceof HttpURLConnection) {
                HttpURLConnection httpURLConnection = (HttpURLConnection) c;
                httpURLConnection.setRequestMethod("GET");

                testValidTokenResponse(httpURLConnection);
            }
        } catch (Exception ex) {
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
}
