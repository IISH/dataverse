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
 *
 * Lets see if we can falsify our assertions.
 *
 * @author Lucien van Wouw <lwo@iisg.nl>
 */
public class ExpirationRuleTest {

    private static long DAY = 86400000L;


    @Test
    public void testPasswordLongLengthNotExpiredWhenNull() {

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
    public void testPasswordWithLongLengthNotExpired300DaysAgo() {

        long passwordModificationDate = new Date().getTime() - DAY * 300;
        ExpirationRule expirationRule = new ExpirationRule(4);
        PasswordData passwordData = new PasswordData();
        passwordData.setPassword("mypassword");
        passwordData.setUsername(String.valueOf(passwordModificationDate));
        PasswordValidator passwordValidator = new PasswordValidator(Collections.singletonList(expirationRule));
        RuleResult validate = passwordValidator.validate(passwordData);
        Assert.assertTrue(validate.isValid());
    }

    @Test
    public void testPasswordWithLongLengthNotExpired400DaysAgo() {

        long passwordModificationDate = new Date().getTime() - DAY * 400;
        ExpirationRule expirationRule = new ExpirationRule();
        PasswordData passwordData = new PasswordData();
        passwordData.setPassword("mypassword");
        passwordData.setUsername(String.valueOf(passwordModificationDate));
        PasswordValidator passwordValidator = new PasswordValidator(Collections.singletonList(expirationRule));
        RuleResult validate = passwordValidator.validate(passwordData);
        Assert.assertTrue(validate.isValid());
    }

    @Test
    public void testPasswordShortLengthNotExpired300DaysAgo() {

        long passwordModificationDate = new Date().getTime() - DAY * 300;
        ExpirationRule expirationRule = new ExpirationRule();
        PasswordData passwordData = new PasswordData();
        passwordData.setPassword("password");
        passwordData.setUsername(String.valueOf(passwordModificationDate));
        PasswordValidator passwordValidator = new PasswordValidator(Collections.singletonList(expirationRule));
        RuleResult validate = passwordValidator.validate(passwordData);
        Assert.assertTrue(validate.isValid());
    }

    @Test
    public void testPasswordShortLengthExpired400DaysAgo() {

        long passwordModificationDate = new Date().getTime() - DAY * 400;
        ExpirationRule expirationRule = new ExpirationRule();
        PasswordData passwordData = new PasswordData();
        passwordData.setPassword("password");
        passwordData.setUsername(String.valueOf(passwordModificationDate));
        PasswordValidator passwordValidator = new PasswordValidator(Collections.singletonList(expirationRule));
        RuleResult validate = passwordValidator.validate(passwordData);
        Assert.assertFalse(validate.isValid());
    }

}
