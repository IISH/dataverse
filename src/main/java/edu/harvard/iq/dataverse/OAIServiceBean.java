package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.groups.Group;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.GuestUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.search.SearchFields;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Named;
import java.util.Set;
import java.util.logging.Logger;

@Stateless
@Named
public class OAIServiceBean {

    private static final Logger logger = Logger.getLogger(OAIServiceBean.class.getCanonicalName());

    /**
     * We're trying to make the OAIServiceBean lean, mean, and fast, with as
     * few injections of EJBs as possible.
     */
    @EJB
    SystemConfig systemConfig;

    @EJB
    GroupServiceBean groupService;

    public String request(User user, Dataverse dataverse, String verb, String identifier, String from, String until, String set, String metadataPrefix) throws SolrServerException {

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

        final SolrQuery query = new SolrQuery().setRequestHandler("/oai");

        if (isSet(verb)) {
            query.add("verb", verb);
        }
        if (isSet(identifier)) {
            query.add("identifier", identifier);
        }
        if (isSet(from)) {
            query.add("from", from);
        }
        if (isSet(until)) {
            query.add("until", until);
        }
        if (isSet((set))) {
            query.add("set", set);
        }
        if (isSet(metadataPrefix)) {
            query.add("metadataPrefix", metadataPrefix);
        }
        if (isSet(permissionFilterQuery)) {
            query.addFilterQuery(permissionFilterQuery);
        }

        // Only look at datasets and Published material
        // We could place these in the static query element in solrconfig.xml/oai
        query.addFilterQuery("dvObjectType", "datasets");
        query.addFilterQuery("publicationStatus", "Published");


        final String baseURL = "http://" + systemConfig.getSolrHostColonPort() + "/solr";
        final SolrServer solrServer = new HttpSolrServer(baseURL);
        final QueryResponse response = solrServer.query(query, SolrRequest.METHOD.GET);
        return response.toString();
    }

    private static boolean isSet(String s) {
        if (s == null)
            return false;
        return !s.trim().isEmpty();
    }

}
