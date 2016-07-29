package edu.harvard.iq.dataverse;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * LowerLengthValidator: applies rules 1, 2 and 3a to passwords with a length equal or greater than PW_EXPIRATION_MIN_LENGTH
 * StandardValidator: applies rules 1, 2 and 3b for passwords with a length less than PW_EXPIRATION_MIN_LENGTH
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
    private static int PW_EXPIRATION_MIN_LENGTH = 10;
    private static int PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD = 20;
    private static int PW_MAX_LENGTH = 255;
    private static int PW_MIN_LENGTH = 8;

    private enum ValidatorTypes {
        GoodStrengthValidator, LowerLengthValidator, StandardValidator
    }

    @SuppressWarnings("unchecked")
    private final static LinkedHashMap<ValidatorTypes, PasswordValidator> cache = new LinkedHashMap(3);
    private int expirationMinLength = PW_EXPIRATION_MIN_LENGTH;
    private int goodStrengthLength = PW_MIN_LENGTH_GOOD_STRENGTH_PASSWORD;
    private int maxLength = PW_MAX_LENGTH;
    private int minLength = PW_MIN_LENGTH;

    @EJB
    SystemConfig systemConfig;

    public void close() {
        cache.clear();
    }


    /**
     * validate
     * <p>
     * Chooses one of the three available validators based on the length of the password and in this order:
     * goodStrengthValidator
     * getLowerLengthValidator
     * getStandardValidator
     *
     * @param passwordModificationTime  The time the password was set or changed.
     * @param password the password to check
     * @return An error message if the validation fails. Otherwise this is null.
     */
    public String validate(String password, long passwordModificationTime) {

        final PasswordValidator passwordValidator = chooseValidator(password.length());

        final PasswordData passwordData = PasswordData.newInstance(password, String.valueOf(passwordModificationTime), null);
        RuleResult validate = passwordValidator.validate(passwordData);
        if (validate.isValid())
            return null;

        final StringBuilder message = new StringBuilder();
        for (RuleResultDetail detail : validate.getDetails()) {
            message.append(detail.toString()).append(" ");
        }
        return message.toString();
    }


    /**
     * chooseValidator
     * <p>
     * Selects the validator based on the password length.
     *
     * @param length Length of the password.
     * @return A PasswordValidator
     */
    private PasswordValidator chooseValidator(int length) {
        if (length >= getGoodStrengthLength() && getGoodStrengthLength() != 0) {
            return getGoodStrengthValidator();
        } else if (length < getExpirationMinLength()) {
            return getLowerLengthValidator();
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
            passwordValidator = new PasswordValidator(rules);
            cache.put(ValidatorTypes.GoodStrengthValidator, passwordValidator);
        }
        return passwordValidator;
    }


    /**
     * getLowerLengthValidator
     * <p>
     * Apply Rules 1, 2 and 3a.
     *
     * @return A PasswordValidator
     */
    private PasswordValidator getLowerLengthValidator() {
        int minLength = getMinLength();
        int maxLength = getMaxLength();
        PasswordValidator passwordValidator = cache.get(ValidatorTypes.LowerLengthValidator);
        if (passwordValidator == null) {
            final WhitespaceRule whitespaceRule = new WhitespaceRule();
            final DictionaryRule dictionaryRule = dictionaryRule();
            final LengthRule lengthRule = new LengthRule(minLength, maxLength);
            final CharacterCharacteristicsRule characteristicsRule = characterRule();
            final ExpirationRule expirationRule = new ExpirationRule();
            final List<Rule> rules = Arrays.asList(whitespaceRule, dictionaryRule, lengthRule, characteristicsRule, expirationRule);
            passwordValidator = new PasswordValidator(rules);
            cache.put(ValidatorTypes.LowerLengthValidator, passwordValidator);
        }
        return passwordValidator;
    }


    /**
     * getStandardValidator
     * <p>
     * Apply Rules 1, 2 and 3b.
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
            final List<Rule> rules = Arrays.asList(whitespaceRule, dictionaryRule, lengthRule, characteristicsRule);
            passwordValidator = new PasswordValidator(rules);
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
     * getExpirationMinLength getter
     * <p>
     * The getExpirationMinLength sets the upper limit where passwords should be validated with an expiration date.
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
