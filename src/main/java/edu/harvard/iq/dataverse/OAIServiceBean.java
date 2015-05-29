package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.GuestUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.search.SearchFields;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Named;
import java.util.logging.Logger;

@Stateless
@Named
public class OAIServiceBean {

    private static final Logger logger = Logger.getLogger(OAIServiceBean.class.getCanonicalName());

    /**
     * We're trying to make the OAIServiceBean lean, mean, and fast, with as
     * few injections of EJBs as possible.
     */
    /**
     * @todo Can we do without the DatasetFieldServiceBean?
     */
    @EJB
    DvObjectServiceBean dvObjectService;
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DatasetServiceBean datasetService;
    @EJB
    DatasetVersionServiceBean datasetVersionService;
    @EJB
    DataFileServiceBean dataFileService;
    @EJB
    DatasetFieldServiceBean datasetFieldService;
    @EJB
    GroupServiceBean groupService;
    @EJB
    SystemConfig systemConfig;

    public static final JsfHelper JH = new JsfHelper();

    public String request(User user, String verb, String identifier, String from, String until, String set, String metadataPrefix) {

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

            if (au.isSuperuser()) {
                // dangerous because this user will be able to see
                // EVERYTHING in Solr with no regard to permissions!
                String dangerZoneNoSolrJoin = null;
                permissionFilterQuery = dangerZoneNoSolrJoin;
            }

        } else {
            logger.info("Should never reach here. A User must be an AuthenticatedUser or a Guest");
        }

        final String baseURL = "http://" + systemConfig.getSolrHostColonPort() + "/solr/oai";
        final SolrQuery query = new SolrQuery();

        if (isSet(verb))
            query.add("verb", verb);
        if (isSet(identifier))
            query.add("identifier", identifier);
        if (isSet(from))
            query.add("from", from);
        if (isSet(until))
            query.add("until", until);
        if (isSet((set)))
            query.add("set", set);
        if (isSet(metadataPrefix))
            query.add("metadataPrefix", metadataPrefix);
        if (isSet(permissionFilterQuery))
            query.addFilterQuery(permissionFilterQuery);

        final SolrServer solrServer = new HttpSolrServer(baseURL);
        QueryResponse queryResponse;
        try {
            queryResponse = solrServer.query(query);
        } catch (SolrServerException e) {
            logger.fine(e.getMessage());
            return null;
        }

        return queryResponse.toString();
    }

    private static boolean isSet(String s) {
        if (s == null)
            return false;
        return !s.trim().isEmpty();
    }

}
