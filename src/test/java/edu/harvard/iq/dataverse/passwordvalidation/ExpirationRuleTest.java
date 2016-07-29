package edu.harvard.iq.dataverse.passwordvalidation;

import edu.harvard.iq.dataverse.validation.ExpirationRule;
import org.junit.Assert;
import org.junit.Test;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.RuleResult;

import java.util.Collections;
import java.util.Date;

/**
 * ExpirationRuleTest
 */
public class ExpirationRuleTest {

    private static long DAY = 86400000L;


    @Test
    public void testPasswordNotExpiredWhenNull() {

        long passwordModificationDate = 0L;
        ExpirationRule expirationRule = new ExpirationRule();
        PasswordData passwordData = new PasswordData();
        passwordData.setPassword("mypassword");
        passwordData.setUsername(String.valueOf(passwordModificationDate));
        PasswordValidator passwordValidator = new PasswordValidator(Collections.singletonList(expirationRule));
        RuleResult validate = passwordValidator.validate(passwordData);
        Assert.assertTrue(validate.isValid());

    }

    @Test
    public void testPasswordNotExpired300DaysAgo() {

        long passwordModificationDate = new Date().getTime() - DAY * 300; // today minus 300 days.
        ExpirationRule expirationRule = new ExpirationRule();
        PasswordData passwordData = new PasswordData();
        passwordData.setPassword("mypassword");
        passwordData.setUsername(String.valueOf(passwordModificationDate));
        PasswordValidator passwordValidator = new PasswordValidator(Collections.singletonList(expirationRule));
        RuleResult validate = passwordValidator.validate(passwordData);
        Assert.assertTrue(validate.isValid());
    }

    @Test
    public void testPasswordExpired() {

        long passwordModificationDate = new Date().getTime() - DAY * 400; // today minus 400 days.
        ExpirationRule expirationRule = new ExpirationRule();
        PasswordData passwordData = new PasswordData();
        passwordData.setPassword("mypassword");
        passwordData.setUsername(String.valueOf(passwordModificationDate));
        PasswordValidator passwordValidator = new PasswordValidator(Collections.singletonList(expirationRule));
        RuleResult validate = passwordValidator.validate(passwordData);
        Assert.assertFalse(validate.isValid());
    }

}
