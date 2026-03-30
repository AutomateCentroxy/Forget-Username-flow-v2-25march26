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

        // Step 1: Validate the email input first (unchanged)
        if (email == null || email.trim().isEmpty()) {
            logger.error("Email input is null or empty");
            return null;
        }

        // Step 2: Fetch user from LDAP (unchanged)
        User user = getUser(MAIL, email);
        if (user == null) {
            logger.warn("No local account found for email: {}", email);
            return null;
        }

        // Step 3: Extract attributes safely (unchanged)
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

        // Step 4: Safely build a fallback name from email (unchanged)
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

        // ── NEW: read active status and phone ─────────────────────────────────
        String jansStatus = getSingleValuedAttr(user, "jansStatus");
        boolean isActive  = ACTIVE_VALUE.equalsIgnoreCase(jansStatus);
        String mobile     = getSingleValuedAttr(user, MOBILE);
        logger.info("User {} jansStatus={} isActive={} phone={}", uid, jansStatus, isActive, mobile);
        // ─────────────────────────────────────────────────────────────────────

        // Step 5: Prepare user data map (unchanged keys + 2 new ones)
        Map<String, String> userMap = new HashMap<>();
        userMap.put(UID,          uid);
        userMap.put(INUM_ATTR,    inum);
        userMap.put("name",       name);
        userMap.put("email",      userEmail);
        userMap.put(DISPLAY_NAME, displayName);
        userMap.put(LAST_NAME,    sn);
        userMap.put(LANG,         lang);
        userMap.put("active",     String.valueOf(isActive)); // NEW — "true"/"false"
        userMap.put("phone",      mobile);                   // NEW — null if not set

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
            String fromNumber = getFromNumberForPhone(phone);
            if (fromNumber == null || fromNumber.trim().isEmpty()) {
                logger.error("FROM_NUMBER not configured for phone {}", phone);
                return false;
            }

            logger.info("Twilio: from={} to={}", fromNumber, phone);
            Twilio.init(flowConfig.get("ACCOUNT_SID"), flowConfig.get("AUTH_TOKEN"));
            Message.creator(
                    new PhoneNumber(phone),
                    new PhoneNumber(fromNumber),
                    message
            ).create();

            logger.info("SMS delivered to {}", phone);
            return true;

        } catch (Exception e) {
            logger.error("Twilio error for {}: {}", phone, e.getMessage(), e);
            return false;
        }
    }

    private String getFromNumberForPhone(String phone) {
        try {
            String defaultFrom   = flowConfig.get("FROM_NUMBER");
            String usCodesStr    = flowConfig.get("US_COUNTRY_CODES");
            String restrictedStr = flowConfig.get("RESTRICTED_COUNTRY_CODES");

            if (defaultFrom == null || defaultFrom.trim().isEmpty()) {
                logger.error("FROM_NUMBER not configured");
                return null;
            }

            Set<String> usSet = parseCodeSet(usCodesStr);
            Set<String> restrictedSet = parseCodeSet(restrictedStr);

            Set<String> allCodes = new HashSet<>();
            allCodes.addAll(usSet);
            allCodes.addAll(restrictedSet);

            String countryCode = extractCountryCode(phone, allCodes);

            if (usSet.contains(countryCode)) {
                String usFrom = flowConfig.get("FROM_NUMBER_US");
                if (usFrom != null && !usFrom.trim().isEmpty()) return usFrom;
            }

            if (restrictedSet.contains(countryCode)) {
                String rFrom = flowConfig.get("FROM_NUMBER_RESTRICTED_COUNTRIES");
                if (rFrom != null && !rFrom.trim().isEmpty()) return rFrom;
            }

            return defaultFrom;

        } catch (Exception ex) {
            logger.error("getFromNumberForPhone error: {}", ex.getMessage(), ex);
            return flowConfig.get("FROM_NUMBER");
        }
    }

    private Set<String> parseCodeSet(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new HashSet<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String extractCountryCode(String phone, Set<String> knownCodes) {
        if (phone == null || phone.trim().isEmpty()) return null;
        String cleaned = phone.startsWith("+") ? phone.substring(1) : phone;
        if (cleaned.length() < 2) return null;

        // US/Canada (+1)
        if (cleaned.startsWith("1") && cleaned.length() > 1
                && Character.isDigit(cleaned.charAt(1))) return "1";

        // 3-digit codes
        if (cleaned.length() >= 3 && knownCodes != null && !knownCodes.isEmpty()) {
            String three = cleaned.substring(0, 3);
            if (knownCodes.contains(three)) return three;
        }

        // Default 2-digit
        return cleaned.substring(0, 2);
    }
}

