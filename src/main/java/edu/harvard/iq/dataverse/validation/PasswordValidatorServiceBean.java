package edu.harvard.iq.dataverse.validation;

import edu.harvard.iq.dataverse.util.SystemConfig;
import org.passay.*;
import org.passay.dictionary.WordListDictionary;
import org.passay.dictionary.WordLists;
import org.passay.dictionary.sort.ArraysSort;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Named;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * PasswordValidatorServiceBean
 * <p>
 * The purpose of this class is to validate passwords according to a set of rules as described in:
 * https://github.com/IQSS/dataverse/issues/3150
 * These contemporary rules govern the way passwords and accounts are protected in order to keep up with current level 3
 * sensitivity data standards.
 * <p>
 * This class will offer presets:
 * Rule 1. It will use a dictionary to block the use of commonly used passwords.
 * Rule 2. It will include at least one character from at least three out of of these four categories:
 * Uppercase letter
 * Lowercase letter
 * Digit
 * Special character ( a whitespace is not a character )
 * Rule 3. It will allow either:
 * a. 8 password length minimum with an annual password expiration
 * b. 10 password length minimum
 * Rule 4. It will forgo all the above three requirements for passwords that have a minimum length of 20.
 * <p>
 * All presets can be tweaked by applying new settings via the admin API of VM arguments.
 * When set VM arguments always overrule admin API settings.
 * <p>
 * Three validator types implement the rulesets.
 * GoodStrengthValidator: applies rule 4 for passwords with a length equal or greater than PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD
 * StandardValidator: applies rules 1, 2 and 3 for passwords with a length less than PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD
 * <p>
 * The password length will determine the validator type.
 * <p>
 * For more information on the library used here, @see http://passay.org
 *
 * @author Lucien van Wouw <lwo@iisg.nl>
 */
@Named
@Stateless
public class PasswordValidatorServiceBean implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(PasswordValidatorServiceBean.class.getCanonicalName());
    private static int PW_EXPIRATION_DAYS = 365;
    private static int PW_EXPIRATION_MIN_LENGTH = 10;
    private static int PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD = 20;
    private static int PW_MAX_LENGTH = 255;
    private static int PW_MIN_LENGTH = 8;

    private enum ValidatorTypes {
        GoodStrengthValidator, LowerLengthValidator, StandardValidator
    }

    @SuppressWarnings("unchecked")
    private final static LinkedHashMap<ValidatorTypes, PasswordValidator> cache = new LinkedHashMap(3);
    private int expirationDays = PW_EXPIRATION_DAYS;
    private int expirationMinLength = PW_EXPIRATION_MIN_LENGTH;
    private int goodStrengthLength = PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD;
    private int maxLength = PW_MAX_LENGTH;
    private int minLength = PW_MIN_LENGTH;
    private PropertiesMessageResolver messageResolver;

    @EJB
    SystemConfig systemConfig;


    public PasswordValidatorServiceBean() {
        final Properties properties = PropertiesMessageResolver.getDefaultProperties();
        properties.setProperty(ExpirationRule.ERROR_CODE_EXPIRED, ExpirationRule.ERROR_MESSAGE_EXPIRED);
        messageResolver = new PropertiesMessageResolver(properties);
    }


    /**
     * validate
     * <p>
     * Validates the password properties and determine if their valid.
     *
     * @param passwordModificationTime  The time the password was set or changed.
     * @param password the password to check
     * @return A List with error messages. Empty when the password is valid.
     */
    public List<String> validate(String password, Date passwordModificationTime) {
        final PasswordData passwordData = PasswordData.newInstance(password, String.valueOf(passwordModificationTime.getTime()), null);
        final PasswordValidator passwordValidator = chooseValidator(password);
        final RuleResult result = passwordValidator.validate(passwordData);
        return passwordValidator.getMessages(result);
    }


    /**
     * chooseValidator
     * <p>
     * Selects the validator based on the password length.
     *
     * @param password A Password
     * @return A PasswordValidator
     */
    private PasswordValidator chooseValidator(String password) {
        final int length = ( password == null ) ? 0 : password.length();
        if (length >= getGoodStrengthLength() && getGoodStrengthLength() != 0) {
            return getGoodStrengthValidator();
        } else {
            return getStandardValidator();
        }
    }


    /**
     * goodStrengthValidator
     * <p>
     * Apply Rule 4: It will forgo all the above three requirements for passwords that have a minimum length of 20.
     *
     * @return A PasswordValidator.
     */
    private PasswordValidator getGoodStrengthValidator() {

        int minLength = getGoodStrengthLength();
        PasswordValidator passwordValidator = cache.get(ValidatorTypes.GoodStrengthValidator);
        if (passwordValidator == null) {
            final WhitespaceRule whitespaceRule = new WhitespaceRule();
            final LengthRule lengthRule = new LengthRule(minLength, getMaxLength());
            final List<Rule> rules = Arrays.asList(whitespaceRule, lengthRule);
            passwordValidator = new PasswordValidator(messageResolver, rules);
            cache.put(ValidatorTypes.GoodStrengthValidator, passwordValidator);
        }
        return passwordValidator;
    }


    /**
     * getStandardValidator
     * <p>
     * Apply Rules 1, 2 and 3.
     *
     * @return A PasswordValidator
     */
    private PasswordValidator getStandardValidator() {
        int minLength = getMinLength();
        int maxLength = getMaxLength();
        PasswordValidator passwordValidator = cache.get(ValidatorTypes.StandardValidator);
        if (passwordValidator == null) {
            final WhitespaceRule whitespaceRule = new WhitespaceRule();
            final DictionaryRule dictionaryRule = dictionaryRule();
            final LengthRule lengthRule = new LengthRule(minLength, maxLength);
            final CharacterCharacteristicsRule characteristicsRule = characterRule();
            final ExpirationRule expirationRule = new ExpirationRule(getExpirationMinLength(), getExpirationDays());
            final List<Rule> rules = Arrays.asList(whitespaceRule, dictionaryRule, lengthRule, characteristicsRule, expirationRule);
            passwordValidator = new PasswordValidator(messageResolver, rules);
            cache.put(ValidatorTypes.StandardValidator, passwordValidator);
        }
        return passwordValidator;
    }


    /**
     * dictionaryRule
     * <p>
     * Reads in the dictionaries from a file.
     *
     * @return A rule.
     */
    private DictionaryRule dictionaryRule() {
        DictionaryRule rule = null;
        try {
            rule = new DictionaryRule(
                    new WordListDictionary(WordLists.createFromReader(
                            dictionaries(),
                            false,
                            new ArraysSort())));
        } catch (IOException e) {
            logger.log(Level.CONFIG, e.getMessage());
        }
        return rule;
    }


    /**
     * dictionaries
     *
     * @return A list of readers for each dictionary.
     */
    private FileReader[] dictionaries() {

        String PwDictionaries = systemConfig.getPwDictionaries();
        if (PwDictionaries == null) {
            final String PW_DICTIONARY_FILES = "weak_passwords.txt";
            final URL url = SystemConfig.class.getResource(PW_DICTIONARY_FILES);
            if (url == null) {
                logger.warning("PasswordValidatorDictionaries not set and no default password file found: " + PW_DICTIONARY_FILES);
                PwDictionaries = PW_DICTIONARY_FILES;
            } else
                PwDictionaries = url.getPath() + File.pathSeparator + url.getFile();
        }

        List<String> files = Arrays.asList(PwDictionaries.split("|"));
        List<FileReader> fileReaders = new ArrayList<>(files.size());
        files.forEach(file -> {
            try {
                fileReaders.add(new FileReader(file));
            } catch (FileNotFoundException e) {
                logger.log(Level.CONFIG, e.getMessage());
            }
        });
        return fileReaders.toArray(new FileReader[fileReaders.size()]);
    }


    /**
     * characterRule
     * <p>
     * Sets a this many 3 from 4 ruleset.
     *
     * @return A CharacterCharacteristicsRule
     */
    private CharacterCharacteristicsRule characterRule() {
        final CharacterCharacteristicsRule characteristicsRule = new CharacterCharacteristicsRule();
        characteristicsRule.setNumberOfCharacteristics(3);
        characteristicsRule.getRules().add(new CharacterRule(EnglishCharacterData.UpperCase, 1));
        characteristicsRule.getRules().add(new CharacterRule(EnglishCharacterData.LowerCase, 1));
        characteristicsRule.getRules().add(new CharacterRule(EnglishCharacterData.Digit, 1));
        characteristicsRule.getRules().add(new CharacterRule(EnglishCharacterData.Special, 1));
        return characteristicsRule;
    }

    /**
     * parseMessages
     * @param messages A list of error messages
     * @return A Human readable string.
     */
    public static String parseMessages(List<String> messages) {
        return messages.stream()
                .map(Object::toString)
                .collect(Collectors.joining(" "));
    }


    /**
     * getGoodStrengthLength
     * <p>
     * Get the length that determines what is a long, hard to brute force password.
     *
     * @return A length
     */
    private int getGoodStrengthLength() {
        int goodStrengthLength = systemConfig.getPwGoodStrengthLength();
        if (goodStrengthLength == -1)
            goodStrengthLength = PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD;
        if (goodStrengthLength != 0 && goodStrengthLength < PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD) {
            logger.log(Level.SEVERE, "The PwGoodStrengthLength " + getGoodStrengthLength() + " value is lower than the" +
                    "current acceptable minimum standard");
            logger.warning("Setting default for PwGoodStrengthLength: " + PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD);
            goodStrengthLength = PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD;
        }
        if (this.goodStrengthLength != goodStrengthLength) {
            this.goodStrengthLength = goodStrengthLength;
            cache.clear();
        }
        return goodStrengthLength;
    }


    /**
     * getMaxLength getter
     * <p>
     * The maximum password length.
     *
     * @return A length
     */
    private int getMaxLength() {
        int maxLength = systemConfig.getPwMaxLength();
        if (maxLength == -1 || maxLength == 0)
            maxLength = PW_MAX_LENGTH;
        if (this.maxLength != maxLength) {
            this.maxLength = maxLength;
            cache.clear();
        }
        return maxLength;
    }


    /**
     * getMinLength getter
     * <p>
     * The minimum password length.
     *
     * @return A length
     */
    private int getMinLength() {
        int minLength = systemConfig.getPwMinLength();
        if (minLength == -1)
            minLength = PW_MIN_LENGTH;
        if (this.minLength != minLength) {
            this.minLength = minLength;
            cache.clear();
        }
        return minLength;
    }


    /**
     * getExpirationDays getter
     * <p>
     * The getExpirationDays sets the number of days a passwords is good after its creation or modification date.
     *
     * @return A unmber
     */
    private int getExpirationDays() {
        int expirationDays = systemConfig.getPwExpirationDays();
        if (expirationDays == -1)
            expirationDays = PW_EXPIRATION_DAYS;
        if (this.expirationDays != expirationDays) {
            this.expirationDays = expirationDays;
            cache.clear();
        }
        return this.expirationDays;
    }


    /**
     * getExpirationMinLength getter
     * <p>
     * The getExpirationMinLength sets the upper limit under which passwords should be validated with an expiration date.
     *
     * @return A length
     */
    private int getExpirationMinLength() {
        int expirationMinLength = systemConfig.getPwExpirationMinLength();
        if (expirationMinLength == -1)
            expirationMinLength = PW_EXPIRATION_MIN_LENGTH;
        if (this.expirationMinLength != expirationMinLength) {
            this.expirationMinLength = expirationMinLength;
            cache.clear();
        }
        return this.expirationMinLength;
    }

}