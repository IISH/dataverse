package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.OAIServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.GuestUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;

import javax.ejb.EJB;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.util.logging.Logger;

/**
 * OAI
 *
 * The API offers a handler for each dataverse name. That means each dataverse instance is treated as a repository
 * with an endpoint: http://domain/api/oai/[dataverse name]
 * and accepts the OAI2 verbs and parameters.
 *
 * As dataverse instances are nested, a harvest will contain all metadata records from the parent and it's children,
 * provided the user has read permissions.
 *
 * User-facing possible documentation:
 * <a href="http://guides.dataverse.org/en/latest/api/oai.html">http://guides.dataverse.org/en/latest/api/oai.html</a>
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
            @QueryParam("key") final String key,
            @Context HttpServletResponse response
    ) {

        User user;
        try {
            user = (key == null) ? new GuestUser() : getUser(key);
        } catch (WrappedResponse ex) {
            throw new ServiceUnavailableException(ex.getMessage());
        }

        response.setHeader("Access-Control-Allow-Origin", "*");
        return oaiService.request(user, key, dataverseService.findRootDataverse(), verb, identifier, from, until, set, metadataPrefix);
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
