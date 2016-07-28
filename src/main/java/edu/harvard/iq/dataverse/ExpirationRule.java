package edu.harvard.iq.dataverse;

import org.passay.PasswordData;
import org.passay.Rule;
import org.passay.RuleResult;

/**
 * ExpirationRule
 *
 *
 *
 */
public class ExpirationRule implements Rule {

    /** Error code for password too short. */
    public static final String ERROR_CODE_EXPIRED = "EXPIRED";

    @Override
    public RuleResult validate(PasswordData passwordData) {

        final RuleResult result = new RuleResult();
        result.setValid(true);
        result.setValid(false);

        return result;
    }
}
