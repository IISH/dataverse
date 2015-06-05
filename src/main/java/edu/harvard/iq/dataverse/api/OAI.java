package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.OAIServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.GuestUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import org.apache.solr.client.solrj.SolrServerException;

import javax.ejb.EJB;
import javax.ws.rs.*;
import java.util.logging.Logger;

/**
 * User-facing documentation:
 * <a href="http://guides.dataverse.org/en/latest/api/search.html">http://guides.dataverse.org/en/latest/api/search.html</a>
 */
@Path("oai")
public class OAI extends AbstractApiBean {

    private static final Logger logger = Logger.getLogger(OAI.class.getCanonicalName());

    @EJB
    OAIServiceBean oaiService;

    @EJB
    DataverseServiceBean dataverseService;

    @GET
    @Produces({"text/xml"})
    public String oai(
            @QueryParam("verb") final String verb,
            @QueryParam("identifier") final String identifier,
            @QueryParam("from") final String from,
            @QueryParam("until") final String until,
            @QueryParam("set") final String set,
            @QueryParam("metadataPrefix") final String metadataPrefix,
            @QueryParam("key") final String key
    ) {

        User user;
        try {
            user = (key == null) ? new GuestUser() : getUser(key);
        } catch (WrappedResponse ex) {
            throw new ServiceUnavailableException(ex.getMessage());
        }


        try {
            return oaiService.request(user,  dataverseService.findRootDataverse(), verb, identifier, from, until, set, metadataPrefix);
        } catch (SolrServerException ex) {
            throw new ServiceUnavailableException(ex.getMessage());
        }
    }

    private User getUser(String key) throws WrappedResponse {
        /**
         * @todo support searching as non-guest:
         * https://github.com/IQSS/dataverse/issues/1299
         *
         * Note that superusers can't currently use the Search API because they
         * see permission documents (all Solr documents, really) and we get a
         * NPE when trying to determine the DvObject type if their query matches
         * a permission document.
         *
         * @todo Check back on https://github.com/IQSS/dataverse/issues/1838 for
         * when/if the Search API is opened up to not require a key.
         */
        AuthenticatedUser authenticatedUser = findUserOrDie(key);
        if (nonPublicSearchAllowed()) {
            return authenticatedUser;
        } else {
            return new GuestUser();
        }
    }

    public boolean nonPublicSearchAllowed() {
        boolean safeDefaultIfKeyNotFound = false;
        return settingsSvc.isTrueForKey(SettingsServiceBean.Key.SearchApiNonPublicAllowed, safeDefaultIfKeyNotFound);
    }

}
