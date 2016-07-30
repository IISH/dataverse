package edu.harvard.iq.dataverse.validation;

import edu.emory.mathcs.backport.java.util.Arrays;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Date;
import java.util.List;

/**
 * PasswordValidatorTest
 * <p>
 * Fire different passwords and settings to the validator service and compare them to an expected outcome.
 *
 * @author Lucien van Wouw <lwo@iisg.nl>
 */
public class PasswordValidatorTest {

    private static PasswordValidatorServiceBean passwordValidatorService;

    class Params {
        boolean valid;
        String password;
        Date passwordModificationTime;
        int expirationDays ;
        int expirationMinLength ;
        int bigLength ;
        int maxLength ;
        int minLength;

        public Params(boolean valid, String password, Date passwordModificationTime, int expirationDays, int expirationMinLength, int bigLength, int maxLength, int minLength) {
            this.valid = valid;
            this.password = password;
            this.passwordModificationTime = passwordModificationTime;
            this.expirationDays = expirationDays;
            this.expirationMinLength = expirationMinLength;
            this.bigLength = bigLength;
            this.maxLength = maxLength;
            this.minLength = minLength;
        }

        public boolean isValid() {
            return valid;
        }

        public String getPassword() {
            return password;
        }

        public Date getPasswordModificationTime() {
            return passwordModificationTime;
        }

        public int getExpirationDays() {
            return expirationDays;
        }

        public int getExpirationMinLength() {
            return expirationMinLength;
        }

        public int getBigLength() {
            return bigLength;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public int getMinLength() {
            return minLength;
        }
    }

    @BeforeClass
    public static void setUp() {
        passwordValidatorService = new PasswordValidatorServiceBean();
        passwordValidatorService.setUseSystemConfig(false);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testA() {

        final Date now = new Date();

        final List<Params> paramsList = Arrays.asList(new Params[]{
                        new Params(false, "one potato", now, -1, -1, -1, -1, -1),
                        new Params(false, "two potato", now, -1, -1, -1, -1, -1)
                }
        );

        paramsList.forEach(
                params -> {
                    passwordValidatorService.setBigLength(params.getBigLength());
                    passwordValidatorService.setExpirationDays(params.getExpirationDays());
                }
        );

    }

}
