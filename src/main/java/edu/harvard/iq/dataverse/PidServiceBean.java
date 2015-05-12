/*
   Copyright (C) 2005-2012, by the President and Fellows of Harvard College.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   Dataverse Network - A web application to share, preserve and analyze research data.
   Developed at the Institute for Quantitative Social Science, Harvard University.
   Version 3.0.
*/

package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.logging.Logger;

/* Handlenet imports: */

/**
 * @author Lucien van Wouw
 *         <p/>
 *         Call the pid webservice to bind the url.
 */
@Stateless
public class PidServiceBean {
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    SettingsServiceBean settingsService;
    private static final Logger logger = Logger.getLogger("edu.harvard.iq.dataverse.HandlenetServiceBean");

    private static final String HANDLE_PROTOCOL_TAG = "hdl";

    private static final String UPSERT_PID_REQUEST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"\n" +
            "                  xmlns:pid=\"http://pid.socialhistoryservices.org/\">\n" +
            "    <soapenv:Body>\n" +
            "        <pid:UpsertPidRequest>\n" +
            "            <pid:na>%s</pid:na>\n" +
            "            <pid:handle>\n" +
            "                <pid:pid>%s</pid:pid>\n" +
            "                 <pid:locAtt>\n" +
            "                     <pid:location href=\"%s\" weight=\"1\"/>\n" +
            "                     <pid:location href=\"%s\" view=\"ddi\" weight=\"0\"/>\n" +
            "                 </pid:locAtt>\n" +
            "            </pid:handle>\n" +
            "        </pid:UpsertPidRequest>\n" +
            "    </soapenv:Body>\n" +
            "</soapenv:Envelope>";

    private static final int CONNECTIONS_PER_ROUTE = 8;
    private org.apache.http.impl.client.DefaultHttpClient httpclient = null;

    public PidServiceBean() {
        httpclient = createThreadSafeClient();
    }

    public void reRegisterHandle(Dataset dataset) {
        if (!HANDLE_PROTOCOL_TAG.equals(dataset.getProtocol())) {
            logger.warning("reRegisterHandle called on a dataset with the non-handle global id: " + dataset.getId());
        }

        logger.info("(Re-)registering an existing handle");

        String pid = dataset.getAuthority()
                + "/" + dataset.getIdentifier();
        String datasetUrl = getRegistrationUrl(dataset);
        String ddiUrl = getDDIUrl(dataset) ;
        String requestBody = String.format(UPSERT_PID_REQUEST, dataset.getAuthority(), pid, datasetUrl, ddiUrl);

        logger.info("Registration URL: " + datasetUrl);

        final String pidwebservice_endpoint = System.getProperty("pidwebservice.endpoint", "localhost");
        final String pidwebservice_bearer = System.getProperty("pidwebservice.bearer", "dummy");

        int statusCode = 0;
        try {
            final HttpUriRequest request = new HttpPost(pidwebservice_endpoint);
            request.setHeader("Content-Type", "text/xml; charset=UTF-8");
            request.setHeader("Authorization", "Bearer " + pidwebservice_bearer);
            request.setHeader("Accept", "*/*");
            final StringEntity myEntity = new StringEntity(requestBody, "UTF-8");
            ((HttpPost) request).setEntity(myEntity);
            final HttpResponse httpResponse = httpclient.execute(request);
            statusCode=  httpResponse.getStatusLine().getStatusCode() ;
        } catch (
                Throwable t
                ) {
            logger.fine("\nError: " + t);
        }

    }


    private String getRegistrationUrl(Dataset dataset) {
        return getSiteUrl() + "/dataset.xhtml?persistentId=hdl:" + dataset.getAuthority()
                + "/" + dataset.getIdentifier();
    }

    private String getDDIUrl(Dataset dataset) {
        return getSiteUrl() + "/api/meta/dataset/" + dataset.getId();
    }

    private String getSiteUrl() {
        String hostUrl = System.getProperty("dataverse.siteUrl");
        if (hostUrl != null && !"".equals(hostUrl)) {
            return hostUrl;
        }
        String hostName = System.getProperty("dataverse.fqdn");
        if (hostName == null) {
            try {
                hostName = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                return null;
            }
        }
        hostUrl = "https://" + hostName;
        return hostUrl;
    }

    /**
     * Generate an HTTP Client for communicating with web services that is
     * thread safe and can be used in the context of a multi-threaded application.
     *
     * @return DefaultHttpClient
     */
    private static DefaultHttpClient createThreadSafeClient() {
        DefaultHttpClient client = new DefaultHttpClient();
        ClientConnectionManager mgr = client.getConnectionManager();
        HttpParams params = client.getParams();
        ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(mgr.getSchemeRegistry());
        connManager.setDefaultMaxPerRoute(CONNECTIONS_PER_ROUTE);
        return new DefaultHttpClient(connManager, params);
    }

    public String createIdentifier() {
        return UUID.randomUUID().toString();
    }

    public void publicizeIdentifier(Dataset dataset) {
        reRegisterHandle(dataset);
    }

    /**
     * Return true if the persistent identifier exists.
     * @param dataset
     * @return
     */
    public boolean pidExists(Dataset dataset) {

        String pid = dataset.getAuthority() + "/" + dataset.getIdentifier();
        // etc.
        return false ;
    }
}



