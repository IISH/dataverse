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
 * If the password is less than a certain length, then its expiration must be validated too.
 *
 * @author Lucien van Wouw <lwo@iisg.nl>
 */
public class ExpirationRule implements Rule {

    /**
     * Error code for password being too short.
     */
    static final String ERROR_CODE_EXPIRED = "EXPIRED";
    static final String ERROR_MESSAGE_EXPIRED = "The password is over %1$s days old and has become expired.";
    private static final long DAY = 86400000L;

    /**
     * expirationMinLength
     * <p>
     * Password less than this length should be checked for an expiration.
     */
    private int expirationMinLength;

    /**
     * expirationDays
     * <p>
     * The number of days the password is valid after the passwords last update or creation time.
     */
    private long expirationDays;

    public ExpirationRule() {
        this.expirationDays = 365; // Good for one year.
        this.expirationMinLength = 10;
    }

    public ExpirationRule(int expirationMinLength) {
        this.expirationDays = 365;
        this.expirationMinLength = expirationMinLength;
    }

    public ExpirationRule(int expirationMinLength, int maxDays) {
        this.expirationMinLength = expirationMinLength;
        this.expirationDays = maxDays;
    }

    @Override
    public RuleResult validate(PasswordData passwordData) {

        final RuleResult result = new RuleResult();

        if (passwordData.getPassword().length() < expirationMinLength) {
            long slidingExpiration = DAY * expirationDays;
            long now = new Date().getTime();
            String username = passwordData.getUsername(); // Admittedly, we abuse the username here to hold the modification time.
            long passwordModificationTime = Long.parseLong(username);
            long expirationTime = passwordModificationTime + slidingExpiration;
            boolean valid = passwordModificationTime == 0 || expirationTime >= now;
            result.setValid(valid);
            if (!valid) {
                result.getDetails().add(new RuleResultDetail(ERROR_CODE_EXPIRED, createRuleResultDetailParameters()));
            }
        } else {
            result.setValid(true);
        }

        return result;
    }

    /**
     * Creates the parameter data for the rule result detail.
     *
     * @return map of parameter name to value
     */
    protected Map<String, Object> createRuleResultDetailParameters() {
        final Map<String, Object> m = new LinkedHashMap<>(1);
        m.put("expirationDays", expirationDays);
        return m;
    }
}
