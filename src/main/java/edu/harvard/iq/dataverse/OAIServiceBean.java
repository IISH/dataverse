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
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Named;
import javax.ws.rs.ServiceUnavailableException;
import javax.xml.ws.http.HTTPException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.logging.Logger;

@Stateless
@Named
/**
 * OAIServiceBean
 *
 * Collect OAI2 arguments, the user and key.
 * Determine what she is allowed to see in the index from the permissions. This is taken from SearchServiceBean.
 * Then call Solr over an HTTP line.
 */
public class OAIServiceBean {

    private static final Logger logger = Logger.getLogger(OAIServiceBean.class.getCanonicalName());
    private static final int HTTP_SOCKET_TIMEOUT = 5000;
    private static final String OAI2_ENDPOINT = "oai.endpoint";
    private static final String IDENTIFY_TEMPLATE = "<OAI-PMH xmlns=\"http://www.openarchives.org/OAI/2.0/\">\n" +
            "<responseDate>%s</responseDate>\n" +
            "<request verb=\"Identify\">%s/api/oai</request>\n" +
            "<Identify>\n" +
            "<repositoryName>%s</repositoryName>\n" +
            "<protocolVersion>2.0</protocolVersion>\n" +
            "<adminEmail>%s</adminEmail>\n" +
            "<earliestDatestamp>1970-01-01T00:00:00Z</earliestDatestamp>\n" +
            "<deletedRecord>transient</deletedRecord>\n" +
            "<granularity>YYYY-MM-DDThh:mm:ssZ</granularity>\n" +
            "<compression>none</compression>\n" +
            "<description>\n" +
            "<oai-identifier:oai-identifier xmlns=\"http://www.openarchives.org/OAI/2.0/oai-identifier\" xmlns:oai-identifier=\"http://www.openarchives.org/OAI/2.0/oai-identifier\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.openarchives.org/OAI/2.0/oai-identifier http://www.openarchives.org/OAI/2.0/oai-identifier.xsd\">\n" +
            "<scheme>oai</scheme>\n" +
            "<repositoryIdentifier>%s</repositoryIdentifier>\n" +
            "<delimiter>:</delimiter>\n" +
            "<sampleIdentifier>oai:%s:doi:10.1234/ABC/DEFGHI</sampleIdentifier>\n" +
            "</oai-identifier:oai-identifier>\n" +
            "</description>\n" +
            "</Identify>\n" +
            "</OAI-PMH>";

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

        if (verb != null && verb.equals("Identify"))
            return identify(
                    systemConfig.getDataverseSiteUrl(),
                    dataverse.getDisplayName(),
                    dataverse.getContactEmails(),
                    System.getProperty("dataverse.fqdn", "localhost"));

        final ModifiableSolrParams params = new ModifiableSolrParams();
        paramsAdd(params, CommonParams.QT, "/oai");  // oai must match the id of the OAI request handler in solrconfig.xml
        paramsAdd(params, "verb", verb);
        paramsAdd(params, "identifier", identifier);
        paramsAdd(params, "from", from);
        paramsAdd(params, "until", until);
        paramsAdd(params, "set", set);
        paramsAdd(params, "metadataPrefix", metadataPrefix);
        paramsAdd(params, CommonParams.FQ, permissionFilter(user, dataverse));
        // The following parameters may be useful for the xslt. Note though: the key will be logged in the Solr log and
        // depending on the infrastructure it is sent to another machine over a plain text connection.
        paramsAdd(params, "siteurl", systemConfig.getDataverseSiteUrl());
        paramsAdd(params, "key", key);

        final GetMethod get = new GetMethod(oaiEndpoint + "?" + params.toString());
        get.getParams().setParameter("http.socket.timeout", HTTP_SOCKET_TIMEOUT);
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

        return body;
    }

    private static void paramsAdd(ModifiableSolrParams params, String key, String value) {
        if (value != null && !value.isEmpty())
            params.add(key, value);
    }

    /**
     * identify
     * <p/>
     * Create an Identify response
     *
     * @param baseUrl              The OAI base url
     * @param repositoryName       Display name of the repository
     * @param adminEmail           The repository administrator ( the dataverse contact )
     * @param repositoryIdentifier The repository identifier
     * @return The Identify response as an XML string
     */
    private static String identify(String baseUrl, String repositoryName, String adminEmail, String repositoryIdentifier) {

        return String.format(IDENTIFY_TEMPLATE,
                parseDatestamp(new Date()),
                baseUrl,
                repositoryName,
                adminEmail,
                repositoryIdentifier,
                repositoryIdentifier
        );
    }


    /**
     * permissionFilter
     * <p/>
     * Taken from SearchServiceBean
     *
     * @return A String to filter the Solr query with
     */
    private String permissionFilter(User user, Dataverse dataverse) {
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

    private static String parseDatestamp(Date date) {

        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:SS'Z'");
        return dateFormat.format(date);
    }
}
