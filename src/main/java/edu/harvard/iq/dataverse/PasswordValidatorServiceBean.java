package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.util.SystemConfig;
import org.passay.*;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;

/**
 * PasswordValidatorLevel3ServiceBean
 * <p>
 * Wrapper for the org.passay.PasswordValidator password validation rule engine.
 * The purpose of this class is to validate passwords according to a set of rules as described in:
 * https://github.com/IQSS/dataverse/issues/3150
 *
 * This class will offer presets that meet the above policies in the following way:
 *
 * 1. It will use a dictionary to block the use of commonly used passwords.
 *
 * 2. It will include at least one character from at least three out of of these four categories:
 * Uppercase letter
 * Lowercase letter
 * Number
 * Special character
 *
 * 3. It will allow either:
 * 10 characters minimum
 * or 8 characters minimum and annual password reset/expiration
 *
 * 4. It will forgo all the above three settings for passwords with 20 characters or more.
 *
 * All presets can be overruled by applying new settings via the API of VM arguments.
 *
 * @author Lucien van Wouw <lwo@iisg.nl>
 */
@Named
@Stateless
public class PasswordValidatorServiceBean implements java.io.Serializable {

    @EJB
    SystemConfig systemConfig;

    final private PasswordValidator passwordValidator;

    public PasswordValidatorServiceBean() {
        final List<Rule> rules = new ArrayList<>(1);
        passwordValidator = new PasswordValidator(rules);
    }

    public String validatePassword(String user, String newPassword) {
        final PasswordData passwordData = PasswordData.newInstance(newPassword, user, null);
        RuleResult validate = passwordValidator.validate(passwordData);
        if ( validate.isValid() )
            return null;

        final StringBuilder message = new StringBuilder();
        for (RuleResultDetail detail : validate.getDetails() ) {
            message.append(detail.toString()).append(" ");
        }
        return message.toString();
    }
}
