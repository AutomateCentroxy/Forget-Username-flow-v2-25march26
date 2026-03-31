package org.gluu.agama.forgetusername;

import io.jans.as.common.service.common.UserService;
import io.jans.as.common.model.common.User;
import io.jans.model.SmtpConfiguration;
import io.jans.service.MailService;
import io.jans.service.cdi.util.CdiUtil;
import io.jans.as.common.service.common.ConfigurationService;
import io.jans.orm.model.base.CustomObjectAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

import org.gluu.agama.smtp.*;
import org.gluu.agama.smtp.EmailUsernameAr;
import org.gluu.agama.smtp.EmailUsernameEn;
import org.gluu.agama.smtp.EmailUsernameEs;
import org.gluu.agama.smtp.EmailUsernameId;
import org.gluu.agama.smtp.EmailUsernamePt;
import org.gluu.agama.smtp.EmailUsernameFr;
import org.gluu.agama.usernameclass.UsernameResendclass;

public class JansForgetUsername extends UsernameResendclass {

    private static final Logger logger = LoggerFactory.getLogger(JansForgetUsername.class);

    // ── Existing constants (unchanged) ────────────────────────────────────────
    private static final String UID          = "uid";
    private static final String INUM_ATTR    = "inum";
    private static final String LANG         = "lang";
    private static final String MAIL         = "mail";
    private static final String DISPLAY_NAME = "displayName";
    private static final String GIVEN_NAME   = "givenName";
    private static final String LAST_NAME    = "sn";

    // ── New constants ──────────────────────────────────────────────────────────
    private static final String MOBILE       = "mobile";
    private static final String ACTIVE_VALUE = "active";
    private static final int    OTP_LENGTH   = 6;

    // OTP store keyed by phone — same pattern as JansUserRegistration
    private static final Map<String, String> userCodes = new HashMap<>();

    // Twilio config injected via constructor
    private final Map<String, String> flowConfig;

    // ── Constructor (replaces no-arg, receives config from Agama flow) ─────────
    public JansForgetUsername(Map<String, String> config) {
        this.flowConfig = config != null ? config : new HashMap<>();
        logger.info("JansForgetUsername initialized. Twilio SID: {}",
                this.flowConfig.get("ACCOUNT_SID"));
    }

    // =========================================================================
    // EXISTING METHOD — getUserEntityByMail
    // Only change: added "active" and "phone" to the returned map
    // =========================================================================
    @Override
    public Map<String, String> getUserEntityByMail(String email) {

        if (email == null || email.trim().isEmpty()) {
            logger.error("Email input is null or empty");
            return null;
        }

        User user = getUser(MAIL, email);
        if (user == null) {
            logger.warn("No local account found for email: {}", email);
            return null;
        }

        String userEmail   = getSingleValuedAttr(user, MAIL);
        String inum        = getSingleValuedAttr(user, INUM_ATTR);
        String name        = getSingleValuedAttr(user, GIVEN_NAME);
        String uid         = getSingleValuedAttr(user, UID);
        if (uid == null || uid.isEmpty()) {
            uid = user.getUserId();
        }
        String displayName = getSingleValuedAttr(user, DISPLAY_NAME);
        String sn          = getSingleValuedAttr(user, LAST_NAME);
        String lang        = getSingleValuedAttr(user, LANG);
        String mobile      = getSingleValuedAttr(user, MOBILE);

        if (name == null) {
            name = displayName;
            if (name == null && userEmail != null) {
                int atIndex = userEmail.indexOf("@");
                if (atIndex > 0) {
                    name = userEmail.substring(0, atIndex);
                } else {
                    logger.warn("Invalid email format for user: {}", userEmail);
                    name = "User";
                }
            }
        }

        // ── FIX: same pattern as isPhoneUnique() in JansUserRegistration ─────────
        UserService userService = CdiUtil.bean(UserService.class);
        User fullUser = userService.getUser(uid, "uid", "jansStatus");

        logger.info("Direct getStatus() = {}", fullUser.getStatus());
        logger.info("getAttribute jansStatus = {}", fullUser.getAttribute("jansStatus", true, false));
        logger.info("getCustomAttribute jansStatus = {}",
            userService.getCustomAttribute(fullUser, "jansStatus") != null
                ? userService.getCustomAttribute(fullUser, "jansStatus").getValue()
                : "NULL");

        String jansStatus = getSingleValuedAttr(fullUser, "jansStatus");
        logger.info("User {} jansStatus={}", uid, jansStatus);

        // null status = active (Janssen default — same logic as isPhoneUnique)
        boolean isActive = jansStatus == null || ACTIVE_VALUE.equalsIgnoreCase(jansStatus);
        logger.info("User {} isActive={} phone={}", uid, isActive, mobile);
        // ─────────────────────────────────────────────────────────────────────────

        Map<String, String> userMap = new HashMap<>();
        userMap.put(UID,          uid);
        userMap.put(INUM_ATTR,    inum);
        userMap.put("name",       name);
        userMap.put("email",      userEmail);
        userMap.put(DISPLAY_NAME, displayName);
        userMap.put(LAST_NAME,    sn);
        userMap.put(LANG,         lang);
        userMap.put("active",     String.valueOf(isActive));
        userMap.put("phone",      mobile);

        logger.info("Returning user data for email: {}", email);
        return userMap;
    }

    // =========================================================================
    // EXISTING METHOD — sendUsernameEmail (completely unchanged)
    // =========================================================================
    @Override
    public boolean sendUsernameEmail(String to, String username, String lang) {
        try {
            ConfigurationService configService = CdiUtil.bean(ConfigurationService.class);
            SmtpConfiguration smtpConfig = configService.getConfiguration().getSmtpConfiguration();

            if (smtpConfig == null) {
                logger.error("SMTP configuration missing.");
                return false;
            }

            String preferredLang = (lang != null && !lang.isEmpty()) ? lang.toLowerCase() : "en";
            Map<String, String> templateData = null;

            switch (preferredLang) {
                case "ar": templateData = EmailUsernameAr.get(username); break;
                case "es": templateData = EmailUsernameEs.get(username); break;
                case "fr": templateData = EmailUsernameFr.get(username); break;
                case "id": templateData = EmailUsernameId.get(username); break;
                case "pt": templateData = EmailUsernamePt.get(username); break;
                default:   templateData = EmailUsernameEn.get(username); break;
            }

            if (templateData == null || !templateData.containsKey("body")) {
                logger.error("No email template found for language: {}", preferredLang);
                return false;
            }

            String subject  = templateData.getOrDefault("subject", "Your Username Information");
            String htmlBody = templateData.get("body");

            if (htmlBody == null || htmlBody.isEmpty()) {
                logger.error("Email HTML body is empty for language: {}", preferredLang);
                return false;
            }

            String textBody = htmlBody.replaceAll("\\<.*?\\>", "");

            MailService mailService = CdiUtil.bean(MailService.class);
            boolean sent = mailService.sendMailSigned(
                    smtpConfig.getFromEmailAddress(),
                    smtpConfig.getFromName(),
                    to, null,
                    subject, textBody, htmlBody
            );

            if (sent) logger.info("Username email sent successfully to {}", to);
            else      logger.error("Failed to send username email to {}", to);

            return sent;

        } catch (Exception e) {
            logger.error("Error sending username email: {}", e.getMessage(), e);
            return false;
        }
    }

    // =========================================================================
    // NEW METHOD — sendOtpToPhone
    // Mirrors sendOTPCode() in JansUserRegistration exactly
    // =========================================================================
    @Override
    public String sendOtpToPhone(String phone, String lang) {
        try {
            logger.info("Sending forget-username OTP to phone: {}", phone);

            String otpCode = generateOtpCode(OTP_LENGTH);
            logger.info("Generated OTP {} for phone {}", otpCode, phone);

            String preferredLang = (lang != null && !lang.isEmpty()) ? lang.toLowerCase() : "en";

            // Localized messages — identical to JansUserRegistration
            Map<String, String> messages = new HashMap<>();
            messages.put("ar", "رمز التحقق OTP الخاص بك من Phi Wallet هو " + otpCode + ". لا تشاركه مع أي شخص.");
            messages.put("en", "Your Phi Wallet OTP is " + otpCode + ". Do not share it with anyone.");
            messages.put("es", "Tu código de Phi Wallet es " + otpCode + ". No lo compartas con nadie.");
            messages.put("fr", "Votre code Phi Wallet est " + otpCode + ". Ne le partagez avec personne.");
            messages.put("id", "Kode Phi Wallet Anda adalah " + otpCode + ". Jangan bagikan kepada siapa pun.");
            messages.put("pt", "O seu código da Phi Wallet é " + otpCode + ". Não o partilhe com ninguém.");

            String message = messages.getOrDefault(preferredLang, messages.get("en"));

            // Store OTP keyed by phone — same as JansUserRegistration
            userCodes.put(phone, otpCode);
            logger.info("OTP stored for phone: {}", phone);

            boolean sent = sendTwilioSms(phone, message);
            if (!sent) {
                logger.error("Twilio failed to deliver OTP to {}", phone);
                return null;
            }

            return phone; // success — mirrors sendOTPCode() return type

        } catch (Exception ex) {
            logger.error("Failed to send OTP to {}: {}", phone, ex.getMessage(), ex);
            return null;
        }
    }

    // =========================================================================
    // NEW METHOD — validateOTPCode
    // Identical to JansUserRegistration.validateOTPCode()
    // =========================================================================
    @Override
    public boolean validateOTPCode(String phone, String code) {
        try {
            logger.info("Validating OTP {} for phone {}", code, phone);
            String storedCode = userCodes.getOrDefault(phone, "NULL");
            logger.info("Submitted: {} — Stored: {}", code, storedCode);

            if (storedCode.equalsIgnoreCase(code)) {
                userCodes.remove(phone); // Invalidate after successful use
                return true;
            }
            return false;

        } catch (Exception ex) {
            logger.error("Error validating OTP for phone {}: {}", phone, ex.getMessage(), ex);
            return false;
        }
    }

    // =========================================================================
    // EXISTING HELPER — getUser (unchanged)
    // =========================================================================
    public User getUser(String attributeName, String value) {
        UserService userService = CdiUtil.bean(UserService.class);
        return userService.getUserByAttribute(attributeName, value, true);
    }

    // =========================================================================
    // EXISTING HELPER — getSingleValuedAttr
    // Extended to handle jansStatus and custom attributes
    // (same upgrade as in JansUserRegistration)
    // =========================================================================
    private String getSingleValuedAttr(User user, String attrName) {
        if (user == null || attrName == null) return null;
        try {
            // jansStatus lives on the User object directly
            if ("jansStatus".equals(attrName)) {
                return user.getStatus() != null ? user.getStatus().getValue() : null;
            }

            Object val = user.getAttribute(attrName, true, false);

            // Fallback to custom attribute path (same as JansUserRegistration)
            if (val == null) {
                UserService userService = CdiUtil.bean(UserService.class);
                CustomObjectAttribute custom = userService.getCustomAttribute(user, attrName);
                if (custom != null) val = custom.getValue();
            }

            if (val instanceof String)                              return (String) val;
            if (val instanceof List && !((List<?>) val).isEmpty()) return (String) ((List<?>) val).get(0);
            if (val != null)                                        return val.toString();

        } catch (Exception e) {
            logger.warn("Error reading attribute {}: {}", attrName, e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // NEW PRIVATE HELPERS — Twilio (mirrors JansUserRegistration exactly)
    // =========================================================================

    private String generateOtpCode(int length) {
        String numbers = "0123456789";
        SecureRandom random = new SecureRandom();
        char[] otp = new char[length];
        for (int i = 0; i < length; i++) {
            otp[i] = numbers.charAt(random.nextInt(numbers.length()));
        }
        return new String(otp);
    }

    private boolean sendTwilioSms(String phone, String message) {
        try {
            // Determine which FROM_NUMBER to use based on country code
            String fromNumber = getFromNumberForPhone(phone);
            
            if (fromNumber == null || fromNumber.trim().isEmpty()) {
                logger.error("FROM_NUMBER is null or empty, cannot send OTP to {}", phone);
                return false;
            }

            PhoneNumber FROM_NUMBER = new com.twilio.type.PhoneNumber(fromNumber);

            logger.info("Sending from: {}", fromNumber);

            PhoneNumber TO_NUMBER = new com.twilio.type.PhoneNumber(phone);

            logger.info("Sending to: {}", phone);

            Twilio.init(flowConfig.get("ACCOUNT_SID"), flowConfig.get("AUTH_TOKEN"));

            Message.creator(TO_NUMBER, FROM_NUMBER, message).create();

            logger.info("OTP code has been successfully sent to {}", phone);

            return true;
        } catch (Exception exception) {
            logger.error("Error sending OTP code to {}: {}", phone, exception.getMessage(), exception);
            return false;
        }
    }

    private String getFromNumberForPhone(String phone) {
        try {
            String defaultFromNumber = flowConfig.get("FROM_NUMBER");
            String usCountryCodes = flowConfig.get("US_COUNTRY_CODES");
            String restrictedCodes = flowConfig.get("RESTRICTED_COUNTRY_CODES");
            
            if (defaultFromNumber == null || defaultFromNumber.trim().isEmpty()) {
                logger.error("FROM_NUMBER not configured");
                return null;
            }
            
            // Parse US country codes for matching
            Set<String> usCountrySet = new HashSet<>();
            if (usCountryCodes != null && !usCountryCodes.trim().isEmpty()) {
                usCountrySet = Arrays.stream(usCountryCodes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
            }
            
            // Parse restricted country codes for matching
            Set<String> restrictedSet = new HashSet<>();
            if (restrictedCodes != null && !restrictedCodes.trim().isEmpty()) {
                restrictedSet = Arrays.stream(restrictedCodes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
            }
            
            // Combine both sets for accurate country code extraction
            Set<String> allKnownCodes = new HashSet<>();
            allKnownCodes.addAll(usCountrySet);
            allKnownCodes.addAll(restrictedSet);
            
            // Extract country code from phone number
            String countryCode = extractCountryCode(phone, allKnownCodes);
            
            if (countryCode == null || countryCode.isEmpty()) {
                return defaultFromNumber;
            }

            // Priority 1: Check if country code is in US_COUNTRY_CODES - use US-specific sender
            if (usCountrySet.contains(countryCode)) {
                String usFromNumber = flowConfig.get("FROM_NUMBER_US");
                
                if (usFromNumber != null && !usFromNumber.trim().isEmpty()) {
                    logger.info("Using US-specific sender {} for country code {}", usFromNumber, countryCode);
                    return usFromNumber;
                }
            }

            // Priority 2: Check if country code is in restricted list
            if (restrictedSet.contains(countryCode)) {
                String restrictedFromNumber = flowConfig.get("FROM_NUMBER_RESTRICTED_COUNTRIES");
                
                if (restrictedFromNumber != null && !restrictedFromNumber.trim().isEmpty()) {
                    logger.info("Using restricted sender {} for country code {}", restrictedFromNumber, countryCode);
                    return restrictedFromNumber;
                }
            }

            return defaultFromNumber;
        } catch (Exception ex) {
            logger.error("Error in getFromNumberForPhone: {}", ex.getMessage(), ex);
            return flowConfig.get("FROM_NUMBER");
        }
    }

    private String extractCountryCode(String phone, Set<String> knownCodes) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }

        String cleaned = phone.startsWith("+") ? phone.substring(1) : phone;
        
        if (cleaned.length() < 2) {
            return null;
        }

        // Handle code "1" first (US/Canada and territories)
        if (cleaned.startsWith("1") && cleaned.length() > 1 && Character.isDigit(cleaned.charAt(1))) {
            return "1";
        }
        
        // Try 3-digit codes ONLY if they're in our knownCodes list
        if (cleaned.length() >= 3 && knownCodes != null && !knownCodes.isEmpty()) {
            String threeDigit = cleaned.substring(0, 3);
            if (knownCodes.contains(threeDigit)) {
                return threeDigit;
            }
        }
        
        // Default: Extract 2-digit country code
        return cleaned.substring(0, 2);
    }
}
