package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.RoleAssignee;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.validator.ValidatorException;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

/**
 * @author Naomi
 * @author Lucien van Wouw <lwo@iisg.nl>
 * @since 2015-07-30
 */
@ViewScoped
@Named
public class SendFeedbackDialog implements java.io.Serializable {

    private static String DEFAULT_RECIPIENT_MAIL = "support@thedata.org";
    private String userEmail = "";
    private String userMessage = "";
    private String messageSubject = "";
    Long op1, op2, userSum;
    // Either the dataverse or the dataset that the message is pertaining to
    // If there is no recipient, this is a general feeback message
    private DvObject recipient;
    private Logger logger = Logger.getLogger(SendFeedbackDialog.class.getCanonicalName());

    private List<String> assignees;
    private List<RoleAssignee> availableRoleAssignees;

    @EJB
    MailServiceBean mailService;
    @EJB
    DataverseServiceBean dataverseService;
    @Inject
    DataverseSession dataverseSession;


    @EJB
    DataverseRoleServiceBean roleService;

    @EJB
    RoleAssigneeServiceBean roleAssigneeService;

    public void setUserEmail(String uEmail) {
        userEmail = uEmail;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void initUserInput(ActionEvent ae) {
        System.out.println("initUserInput()");
        userEmail = "";
        userMessage = "";
        messageSubject = "";
        Random random = new Random();
        op1 = new Long(random.nextInt(10));
        op2 = new Long(random.nextInt(10));
        userSum = null;
        availableRoleAssignees = new ArrayList<>(1);
        assignees = new ArrayList<>();
    }

    public Long getOp1() {
        return op1;
    }

    public void setOp1(Long op1) {
        this.op1 = op1;
    }

    public Long getOp2() {
        return op2;
    }

    public void setOp2(Long op2) {
        this.op2 = op2;
    }

    public Long getUserSum() {
        return userSum;
    }

    public void setUserSum(Long userSum) {
        this.userSum = userSum;
    }


    public String getMessageTo() {
        if (recipient == null) {
            return JH.localize("contact.support");
        } else if (recipient.isInstanceofDataverse()) {
            return ((Dataverse) recipient).getDisplayName() + " " + JH.localize("contact.contact");
        } else
            return JH.localize("dataset") + " " + JH.localize("contact.contact");
    }

    public String getFormHeader() {
        if (recipient == null) {
            return JH.localize("contact.header");
        } else if (recipient.isInstanceofDataverse()) {
            return JH.localize("contact.dataverse.header");
        } else
            return JH.localize("contact.dataset.header");
    }

    public void setUserMessage(String mess) {
        System.out.println("setUserMessage: " + mess);
        userMessage = mess;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setMessageSubject(String messageSubject) {
        this.messageSubject = messageSubject;
    }

    public String getMessageSubject() {
        return messageSubject;
    }

    public boolean isLoggedIn() {
        return dataverseSession.getUser().isAuthenticated();
    }

    public String loggedInUserEmail() {
        return dataverseSession.getUser().getDisplayInfo().getEmailAddress();
    }

    public DvObject getRecipient() {
        return recipient;
    }

    public void setRecipient(DvObject recipient) {
        this.recipient = recipient;
    }

    private String getDataverseEmail(Dataverse dataverse) {
        String email = "";

        for (DataverseContact dc : dataverse.getDataverseContacts()) {
            if (!email.isEmpty()) {
                email += ",";
            }
            email += dc.getContactEmail();
        }
        return email;
    }

    public void validateUserSum(FacesContext context, UIComponent component, Object value) throws ValidatorException {

        if (op1 + op2 != (Long) value) {

            FacesMessage msg
                    = new FacesMessage("Sum is incorrect, please try again.");
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);

            throw new ValidatorException(msg);
        }

    }

  public void validateUserEmail(FacesContext context, UIComponent component, Object value) throws ValidatorException {

        if (!EmailValidator.getInstance().isValid((String) value)) {

            FacesMessage msg
                    = new FacesMessage("Invalid email.");
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);

            throw new ValidatorException(msg);
        }

    }

    public void validateRoleAssignees(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        if (((List) value).isEmpty()) {
            FacesMessage msg
                    = new FacesMessage("Select at least one recipient.");
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);

            throw new ValidatorException(msg);
        }
    }

    public String sendMessage() {
        final List<String> emailList = new ArrayList<>();

        if (assignees.isEmpty()) {
            if (recipient != null) {
                if (recipient.isInstanceofDataverse()) {
                    addEmail(emailList, getDataverseEmail((Dataverse) recipient));
                } else if (recipient.isInstanceofDataset()) {
                    Dataset d = (Dataset) recipient;
                    for (DatasetField df : d.getLatestVersion().getFlatDatasetFields()) {
                        if (df.getDatasetFieldType().getName().equals(DatasetFieldConstant.datasetContactEmail)) {
                            addEmail(emailList, df.getValue());
                        }
                    }

                    if (emailList.isEmpty()) {
                        addEmail(emailList, getDataverseEmail(d.getOwner()));
                    }
                }
            }


            if (emailList.isEmpty()) {
                addEmail(emailList, DEFAULT_RECIPIENT_MAIL);
            }
        } else {
            for (String assigneeIdentifier : assignees) {
                final String mail = getEmailByIdentifier(assigneeIdentifier);
                if (mail == null)
                    logger.info("Could not find role assignee in available list of assignees: " + assigneeIdentifier);
                else
                    addEmail(emailList, mail);
            }
        }

        String recipients = StringUtils.join(emailList, ",");

        if (isLoggedIn() && userMessage != null) {
            mailService.sendMail(loggedInUserEmail(), recipients, getMessageSubject(), userMessage);
            userMessage = "";
            return null;
        } else {
            if (userEmail != null && userMessage != null) {
                mailService.sendMail(userEmail, recipients, getMessageSubject(), userMessage);
                userMessage = "";
                return null;
            } else {
                userMessage = "";
                return null;
            }
        }
    }

    private static void addEmail(List<String> email, String recipient) {
        if (!email.contains(recipient))
            email.add(recipient);
    }

    /**
     * getEmailByIdentifier
     * <p/>
     * Assuming this list will not be long.
     *
     * @param identifier of the recipient.
     * @return An e-mail address if the recipient is in availableRoleAssignees list.
     */
    private String getEmailByIdentifier(String identifier) {
        for (RoleAssignee roleAssignee : availableRoleAssignees) {
            if (roleAssignee.getIdentifier().equals(identifier))
                return roleAssignee.getDisplayInfo().getEmailAddress();
        }
        return null;
    }

    /**
     * getRoleAssignees
     * <p/>
     * Create a list of users that have a role for this dataverse object.
     *
     * @return A list of Titles ( firstname and lastname ) and user identifiers
     */
    public List<RoleAssignee> getAvailableRoleAssignees() {

        if (availableRoleAssignees.isEmpty() && isAuthorized()) {
            final Set<RoleAssignment> ras = roleService.rolesAssignments(recipient);
            for (RoleAssignment roleAssignment : ras) {
                final RoleAssignee roleAssignee = roleAssigneeService.getRoleAssignee(roleAssignment.getAssigneeIdentifier());
                if (roleAssignee != null) {
                    if (!availableRoleAssignees.contains(roleAssignee))
                        availableRoleAssignees.add(roleAssignee);
                } else {
                    logger.info("Could not find role assignee based on role assignment id " + roleAssignment.getId());
                }
            }
        }
        return availableRoleAssignees;
    }

    public List<String> getRoleAssignees() {
        return this.assignees;
    }

    public void setRoleAssignees(List<String> assignees) {
        this.assignees = assignees;
    }

    /**
     * Determine if the user has a role assigned to this dataverse object.
     *
     * @return True or false if it is not so.
     */
    public boolean isAuthorized() {
        return
                recipient != null
                        && isLoggedIn()
                        && !roleService.assignmentsFor(dataverseSession.getUser(), recipient).isEmpty();
    }

}
