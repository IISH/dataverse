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
 * Collect OAI2 arguments and the user by key.
 * With the key, retrieve the user and determine what she is allowed to see in the index from the permissions.
 * Then call Solr over an HTTP line.
 */
public class OAIServiceBean {

    private static final Logger logger = Logger.getLogger(OAIServiceBean.class.getCanonicalName());
    private static final Integer HTTP_SOCKET_TIMEOUT = 5000;
    private static final String OAI2_ENDPOINT = "oai";
    private static final String API_METADATA_DATASET = "/api/meta/dataset/";

    private HttpClient client;
    private String oaiEndpoint;

    @EJB
    SystemConfig systemConfig;

    @EJB
    GroupServiceBean groupService;

    public OAIServiceBean() {
        final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
        this.client = new HttpClient(connectionManager);
        this.oaiEndpoint = System.getProperty(OAI2_ENDPOINT, "http://localhost:8983/solr/collection1/oai");// todo make the OAI2_ENDPOINT a SettingsServiceBean key.
    }

    /**
     * request
     *
     * @param user      The client User
     * @param key       API token
     * @param dataverse The dataverse repository we are harvesting from
     * @return A valid XML document as String
     * @throws ServiceUnavailableException
     */
    public String request(User user, String key, Dataverse dataverse, String verb, String identifier, String from, String until, String set, String metadataPrefix) throws ServiceUnavailableException {

        final String permissionFilterQuery = buildPemissionQueryString(user, dataverse);

        final HttpMethodParams params = new HttpMethodParams();
        params.setParameter(CommonParams.QT, "/oai");  // oai must match the id of the OAI request handler in solrconfig.xml
        params.setParameter("verb", verb);
        params.setParameter("identifier", identifier);
        params.setParameter("from", from);
        params.setParameter("until", until);
        params.setParameter("set", set);
        params.setParameter("metadataPrefix", metadataPrefix);
        params.setParameter(CommonParams.FQ, permissionFilterQuery);

        // Add the api endpoint, key and timeout.
        params.setParameter("siteurl_api", systemConfig.getDataverseSiteUrl() + API_METADATA_DATASET);
        params.setParameter("key", key);
        params.setParameter("http.socket.timeout", HTTP_SOCKET_TIMEOUT);

        final GetMethod get = new GetMethod(oaiEndpoint);
        get.setParams(params);
        String body = null;
        try {
            client.executeMethod(get);
            body = getBodyFromInputStream(get.getResponseBodyAsStream());
        } catch (IOException | HTTPException e) {
            logger.fine(e.getMessage());
        } finally {
            // release the connection back to the connection manager
            get.releaseConnection();
        }

        if (body == null) {
            throw new ServiceUnavailableException("Unable to get a valid Solr response.");
        }

        return body;
    }

    private String identify() {

    }


    /**
     * buildPemissionQueryString
     * <p/>
     * Taken from SearchServiceBean
     *
     * @return A String to filter the Solr query with
     */
    private String buildPemissionQueryString(User user, Dataverse dataverse) {
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
        return permissionFilterQuery;
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

}
