package edu.harvard.iq.dataverse.validation;

import org.passay.PasswordData;
import org.passay.Rule;
import org.passay.RuleResult;
import org.passay.RuleResultDetail;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ExpirationRule
 * <p>
 * The username is used to carry the timestamp.
 * The password is good then its expiration date is in the future compared to the current system date and the slider offset.
 *
 * Admittedly, we abuse the username value here to store the expirationTime
 */
public class ExpirationRule implements Rule {

    /**
     * Error code for password being too short.
     */
    public static final String ERROR_CODE_EXPIRED = "EXPIRED";
    public static final String ERROR_MESSAGE_EXPIRED = "The password is expired and should be changed.";
    private static long SLIDER = 31556926000L; // One year.
    private long slidingExpiration;

    public ExpirationRule() {
        this.slidingExpiration = SLIDER;
    }

    public ExpirationRule(long slidingExpiration) {
        this.slidingExpiration = slidingExpiration;
    }

    @Override
    public RuleResult validate(PasswordData passwordData) {

        final RuleResult result = new RuleResult();

        long now = new Date().getTime();
        String username = passwordData.getUsername();
        long passwordModificationTime = Long.parseLong(username);
        long expirationTime = (passwordModificationTime == 0) ? now : passwordModificationTime + slidingExpiration;
        boolean valid = expirationTime >= now;
        result.setValid(valid);
        if (!valid) {
            result.getDetails().add(new RuleResultDetail(ERROR_CODE_EXPIRED, createRuleResultDetailParameters()));
        }

        return result;
    }

    /**
     * Creates the parameter data for the rule result detail.
     *
     * @return  map of parameter name to value
     */
    protected Map<String, Object> createRuleResultDetailParameters()
    {
        final Map<String, Object> m = new LinkedHashMap<>(1);
        m.put("slidingExpiration", slidingExpiration);
        return m;
    }
}
