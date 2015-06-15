package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.groups.Group;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.GuestUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.search.SearchFields;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.solr.common.params.CommonParams;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Named;
import javax.ws.rs.ServiceUnavailableException;
import javax.xml.ws.http.HTTPException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.logging.Logger;

@Stateless
@Named
/**
 * OAIServiceBean
 *
 * Collect OAI2 arguments and the user. Determine what the user is allowed to see in the index from the permissions.
 * Then call Solr over an HTTP line.
 *
 */
public class OAIServiceBean {

    private static final Logger logger = Logger.getLogger(OAIServiceBean.class.getCanonicalName());
    private static final String OAI2_ENDPOINT = "oai";
    private static final Integer HTTP_SOCKET_TIMEOUT = 5000;

    private org.apache.commons.httpclient.HttpClient client;
    private String oaiEndpoint = null;

    /**
     * We're trying to make the OAIServiceBean lean, mean, and fast, with as
     * few injections of EJBs as possible.
     */
    @EJB
    SystemConfig systemConfig;

    @EJB
    GroupServiceBean groupService;

    public OAIServiceBean() {
        final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
        setClient(new HttpClient(connectionManager));
        setOaiEndpoint(System.getProperty(OAI2_ENDPOINT, "http://localhost:8983/solr/collection1/oai"));// todo make the OAI2_ENDPOINT a SettingsServiceBean key.
    }

    public String request(User user, String key, Dataverse dataverse, String verb, String identifier, String from, String until, String set, String metadataPrefix) throws ServiceUnavailableException {

        String publicOnly = "{!join from=" + SearchFields.DEFINITION_POINT + " to=id}" + SearchFields.DISCOVERABLE_BY + ":(" + IndexServiceBean.getPublicGroupString() + ")";
        // initialize to public only to be safe
        String permissionFilterQuery = publicOnly;
        if (user instanceof GuestUser) {
            permissionFilterQuery = publicOnly;
        } else if (user instanceof AuthenticatedUser) {
            // Non-guests might get more than public stuff with an OR or two
            AuthenticatedUser au = (AuthenticatedUser) user;

            // safe default: public only
            String publicPlusUserPrivateGroup = publicOnly;
            permissionFilterQuery = publicPlusUserPrivateGroup;
            logger.fine(permissionFilterQuery);

            String groupsFromProviders = "";
            Set<Group> groups = groupService.groupsFor(au, dataverse);
            StringBuilder sb = new StringBuilder();
            for (Group group : groups) {
                logger.fine("found group " + group.getIdentifier() + " with alias " + group.getAlias());
                String groupAlias = group.getAlias();
                if (groupAlias != null && !groupAlias.isEmpty()) {
                    sb.append(" OR ");
                    // i.e. group_shib/2
                    sb.append(IndexServiceBean.getGroupPrefix()).append(groupAlias);
                }
                groupsFromProviders = sb.toString();
            }

            logger.fine(groupsFromProviders);
            publicPlusUserPrivateGroup = "{!join from=" + SearchFields.DEFINITION_POINT + " to=id}" + SearchFields.DISCOVERABLE_BY + ":(" + IndexServiceBean.getPublicGroupString() + " OR " + IndexServiceBean.getGroupPerUserPrefix() + au.getId() + groupsFromProviders + ")";

            permissionFilterQuery = publicPlusUserPrivateGroup;

            if (au.isSuperuser()) {
                // dangerous because this user will be able to see
                // EVERYTHING in Solr with no regard to permissions!
                permissionFilterQuery = null;
            }

        } else {
            logger.info("Should never reach here. A User must be an AuthenticatedUser or a Guest");
        }

        final HttpMethodParams params = new HttpMethodParams();
        params.setParameter(CommonParams.QT, "/oai");  // assuming the OAI request handler was set in solrconfig.xml with this id.
        if (isSet(verb)) {
            params.setParameter("verb", verb);
        }
        if (isSet(identifier)) {
            params.setParameter("identifier", identifier);
        }
        if (isSet(from)) {
            params.setParameter("from", from);
        }
        if (isSet(until)) {
            params.setParameter("until", until);
        }
        if (isSet((set))) {
            params.setParameter("set", set);
        }
        if (isSet(metadataPrefix)) {
            params.setParameter("metadataPrefix", metadataPrefix);
        }
        if (isSet(permissionFilterQuery)) {
            params.setParameter(CommonParams.FQ, permissionFilterQuery);
        }
        // Add the api endpoint.
        params.setParameter("siteurl_api", systemConfig.getDataverseSiteUrl() + "/api/meta/dataset/");

        final GetMethod get = new GetMethod(oaiEndpoint);
        String body = null;
        get.getParams().setParameter("http.socket.timeout", HTTP_SOCKET_TIMEOUT);
        try {
            client.executeMethod(get);
            body = getBodyFromInputStream(get.getResponseBodyAsStream());
        } catch (IOException | HTTPException e) {
            logger.fine(e.getMessage());
        } finally {
            // released the connection back to the connection manager
            get.releaseConnection();
        }

        if (body == null) {
            throw new ServiceUnavailableException("Unable to get a 200 Solr response.");
        }

        return body;
    }

    private static String getBodyFromInputStream(InputStream is) {

        BufferedReader br = null;
        final StringBuilder sb = new StringBuilder();

        String line;
        try {
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            logger.fine(e.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    logger.fine(e.getMessage());
                }
            }
        }

        return sb.toString();
    }

    private static boolean isSet(String s) {
        return s != null && !s.trim().isEmpty();
    }

    public void setClient(HttpClient client) {
        this.client = client;
    }

    public void setOaiEndpoint(String oaiEndpoint) {
        this.oaiEndpoint = oaiEndpoint;
    }
}
