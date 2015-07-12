package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.OAIServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.GuestUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.engine.command.impl.GetDataverseCommand;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * OAI
 * <p/>
 * The API offers a handler for each dataverse name. That means each dataverse instance is treated as a repository
 * with an endpoint: http://domain/api/oai/[dataverse name (Default is root)]
 * and accepts the OAI2 verbs and parameters.
 * <p/>
 * User-facing possible documentation at a place like:
 * <a href="http://guides.dataverse.org/en/latest/api/oai.html">http://guides.dataverse.org/en/latest/api/oai.html</a>
 *
 * @author Lucien van Wouw <lwo@iisg.nl>
 */
@Path("oai")
public class OAI extends AbstractApiBean {

    //private static final Logger logger = Logger.getLogger(OAI.class.getCanonicalName());

    @EJB
    OAIServiceBean oaiService;

    @GET
    public Response oai(@QueryParam("verb") final String verb,
                        @QueryParam("identifier") final String identifier,
                        @QueryParam("from") final String from,
                        @QueryParam("until") final String until,
                        @QueryParam("set") final String set,
                        @QueryParam("metadataPrefix") final String metadataPrefix,
                        @QueryParam("key") final String key) {
        return oai(dataverseSvc.findRootDataverse(), verb, identifier, from, until, set, metadataPrefix, key);
    }

    @GET
    @Path("{dataverse}")
    public Response oai(@PathParam("dataverse") final String dvIdtf,
                        @QueryParam("verb") final String verb,
                        @QueryParam("identifier") final String identifier,
                        @QueryParam("from") final String from,
                        @QueryParam("until") final String until,
                        @QueryParam("set") final String set,
                        @QueryParam("metadataPrefix") final String metadataPrefix,
                        @QueryParam("key") final String key) {

        final Dataverse dataverse = findDataverse(dvIdtf);
        if (dataverse == null) {
            return errorResponse(Response.Status.NOT_FOUND, "Dataverse not found '" + key + "'");
        }

        return oai(dataverse, verb, identifier, from, until, set, metadataPrefix, key);
    }

    /**
     * oai
     * <p/>
     * Find the user and see if that user is allowed to read the dataverse.
     */
    private Response oai(Dataverse dataverse, String verb, String identifier, String from, String until, String set, String metadataPrefix, String key) {

        User user;
        try {
            user = (key == null) ? new GuestUser() : getUser(key);
        } catch (WrappedResponse ex) {
            return errorResponse(Response.Status.UNAUTHORIZED, "Bad api key '" + key + "'");
        }

        if (verb.equals("identify")) { // See if we can give information about the dataverse for this user.
            try {
                dataverse = execCommand(new GetDataverseCommand(user, dataverse), "Get Dataverse");
            } catch (WrappedResponse ex) {
                return errorResponse(Response.Status.UNAUTHORIZED, "User is not allowed to view '" + dataverse.getName() + "'");
            }
        }

        final String xml = oaiService.request(user, key, dataverse, verb, identifier, from, until, set, metadataPrefix);
        if (xml == null) {
            return errorResponse(Response.Status.SERVICE_UNAVAILABLE, "Failed to get a response from the OAI2Service.");
        }
        return okResponse(xml);
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
        final boolean safeDefaultIfKeyNotFound = false;
        return settingsSvc.isTrueForKey(SettingsServiceBean.Key.SearchApiNonPublicAllowed, safeDefaultIfKeyNotFound);
    }

    @Override
    protected Response okResponse(String msg) {

        return Response.ok().entity(msg)
                .type(MediaType.TEXT_XML)
                .build();
    }
}
