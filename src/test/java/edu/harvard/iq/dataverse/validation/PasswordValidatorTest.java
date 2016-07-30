package edu.harvard.iq.dataverse.validation;

import edu.emory.mathcs.backport.java.util.Arrays;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    private class Params {
        int numberOfExpectedErrors;
        String password;
        Date passwordModificationTime;
        int expirationDays;
        int expirationMinLength;
        int bigLength;
        int maxLength;
        int minLength;
        String dictionaries;

        Params(int numberOfExpectedErrors, String password, Date passwordModificationTime, int expirationDays, int expirationMinLength, int bigLength, int maxLength, int minLength, String dictionaries) {
            this.numberOfExpectedErrors = numberOfExpectedErrors;
            this.password = password;
            this.passwordModificationTime = passwordModificationTime;
            this.expirationDays = expirationDays;
            this.expirationMinLength = expirationMinLength;
            this.bigLength = bigLength;
            this.maxLength = maxLength;
            this.minLength = minLength;
            this.dictionaries = dictionaries;
        }

        int getNumberOfExpectedErrors() {
            return numberOfExpectedErrors;
        }

        String getPassword() {
            return password;
        }

        Date getPasswordModificationTime() {
            return passwordModificationTime;
        }

        int getExpirationDays() {
            return expirationDays;
        }

        int getExpirationMinLength() {
            return expirationMinLength;
        }

        int getBigLength() {
            return bigLength;
        }

        int getMaxLength() {
            return maxLength;
        }

        int getMinLength() {
            return minLength;
        }

        String getDictionaries() {
            return dictionaries;
        }
    }

    @BeforeClass
    public static void setUp() {
        passwordValidatorService = new PasswordValidatorServiceBean();
        passwordValidatorService.setUseSystemConfig(false);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDifferentPasswordsAndSettings() {

        long DAY = 86400000L;
        final Date expired = new Date(new Date().getTime() - DAY * 400);
        final Date not_expired = new Date(new Date().getTime() - DAY * 300);


        // -1 is not set ( hence the defaults kick in )
        // 0 is disabled
        final List<Params> paramsList = Arrays.asList(new Params[]{
                        new Params(7, "p otato", expired, -1, -1, 0, -1, -1, null), // everything wrong here
                        new Params(6, "p otato", expired, 401, -1, 0, -1, -1, null), // 401 days before expiration
                        new Params(6, "p otato", not_expired, -1, -1, 0, -1, -1, null),
                        new Params(5, "one potato", not_expired, -1, -1, 0, -1, -1, null),
                        new Params(4, "Two potato", not_expired, -1, -1, 0, -1, -1, null),
                        new Params(0, "Three.potato", not_expired, -1, -1, 0, -1, -1, null),
                        new Params(0, "F0ur.potato", not_expired, -1, 15, 0, -1, 10, null),
                        new Params(1, "F0ur.potato", expired, -1, 15, 0, -1, 10, null),
                        new Params(0, "4.potato", not_expired, -1, -1, 0, -1, -1, null),
                        new Params(0, "55Potato", not_expired, -1, -1, 0, -1, -1, null),
                        new Params(1, "55Potato", not_expired, -1, -1, 0, -1, -1, createDictionary("55potato", false)), // password in dictionary
                        new Params(1, "6 Potato", not_expired, -1, -1, -1, -1, -1, null),
                        new Params(1, "Potato.Too.12345.Short", not_expired, -1, -1, 0, -1, 100, null),
                        new Params(0, "Potatoes on my plate", expired, -1, -1, 20, -1, -1, null),
                        new Params(0, "Potatoes on a plate.", expired, -1, -1, 20, -1, -1, null),
                        new Params(0, "Potatoes on a plate ", expired, -1, -1, 20, -1, -1, null)
                }
        );

        paramsList.forEach(
                params -> {
                    passwordValidatorService.setBigLength(params.getBigLength());
                    passwordValidatorService.setExpirationDays(params.getExpirationDays());
                    passwordValidatorService.setExpirationMinLength(params.getExpirationMinLength());
                    passwordValidatorService.setMaxLength(params.getMaxLength());
                    passwordValidatorService.setMinLength(params.getMinLength());
                    passwordValidatorService.setDictionaries(params.getDictionaries());
                    List<String> errors = passwordValidatorService.validate(params.getPassword(), params.getPasswordModificationTime());
                    Assert.assertTrue(message(errors), actualSize(errors) == params.getNumberOfExpectedErrors());
                }
        );

    }

    /**
     * createDictionary
     *
     * Create a dictionary with a password
     *
     * @param password The string to add
     * @return The absolute file path of the dictionary file.
     */
    private String createDictionary(String password, boolean append) {
        File file = null;
        try {
            file = File.createTempFile("weak_password_dictionary", ".txt");
            FileOutputStream fileOutputStream = new FileOutputStream(file, append);
            fileOutputStream.write(password.getBytes());
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert file != null;
        return file.getAbsolutePath();
    }

    private int actualSize(List<String> errors) {
        return (errors == null) ? 0 : errors.size();
    }

    private String message(List<String> errors) {
        return (errors == null) ? "No error" : PasswordValidatorServiceBean.parseMessages(errors);
    }

}
