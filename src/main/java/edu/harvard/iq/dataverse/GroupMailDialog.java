package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.groups.Group;

import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.util.Set;

/**
 * GroupMailDialog
 *
 * Send a mail to a group of people are a member of the given Dataverse or dataset.
 *
 * @author Lucien van Wouw <lwo@iisg.nl>
 */
@ViewScoped
@Named
public class GroupMailDialog extends SendFeedbackDialog {


    private boolean isAuthorized() {
        Set<Group> groupMemberships = dataverseSession.getUser().getRequestMetadata().getGroupMemberships();
        return dataverseSession.getUser().isAuthenticated();
    }

    public String getFormFragment() {


     return ( isAuthorized() ) ? "groupmailFormFragment.xhtml" : "contactFormFragment.xhtml";

    }
}
