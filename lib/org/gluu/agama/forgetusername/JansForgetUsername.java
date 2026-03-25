package org.gluu.agama.forgetusername;

import io.jans.as.common.service.common.UserService;
import io.jans.as.common.model.common.User;
import io.jans.model.SmtpConfiguration;
import io.jans.service.MailService;
import io.jans.service.cdi.util.CdiUtil;
import io.jans.as.common.service.common.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
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

    private static final String UID = "uid";
    private static final String INUM_ATTR = "inum";
    private static final String LANG = "lang";
    private static final String MAIL = "mail";
    private static final String DISPLAY_NAME = "displayName";
    private static final String GIVEN_NAME = "givenName";
    private static final String LAST_NAME = "sn";

    // Helper method to get user by any attribute (like UID, MAIL, INUM)
    public User getUser(String attributeName, String value) {
        UserService userService = CdiUtil.bean(UserService.class);
        return userService.getUserByAttribute(attributeName, value, true);
    }

    // Helper method to safely get single-valued attributes
    private String getSingleValuedAttr(User user, String attrName) {
        if (user == null || attrName == null) return null;
        try {
            Object val = user.getAttribute(attrName);
            if (val instanceof String) {
                return (String) val;
            } else if (val instanceof List && !((List<?>) val).isEmpty()) {
                return (String) ((List<?>) val).get(0);
            }
        } catch (Exception e) {
            logger.warn("Error reading attribute {}: {}", attrName, e.getMessage());
        }
        return null;
    }

    @Override
    public Map<String, String> getUserEntityByMail(String email) {
        Map<String, String> userMap = new HashMap<>();

        // Step 1: Validate the email input first
        if (email == null || email.trim().isEmpty()) {
            logger.error("Email input is null or empty");
            return null;
        }

        // Step 2: Fetch user from LDAP
        User user = getUser(MAIL, email);
        if (user == null) {
            logger.warn("No local account found for email: {}", email);
            return null; // Return null so Agama 'Otherwise' block triggers
        }

        // Step 3: Extract attributes safely
        String userEmail = getSingleValuedAttr(user, MAIL);
        String inum = getSingleValuedAttr(user, INUM_ATTR);
        String name = getSingleValuedAttr(user, GIVEN_NAME);
        String uid = getSingleValuedAttr(user, UID);
        if (uid == null || uid.isEmpty()) {
            uid = user.getUserId();
        }
        String displayName = getSingleValuedAttr(user, DISPLAY_NAME);
        String sn = getSingleValuedAttr(user, LAST_NAME);
        String lang = getSingleValuedAttr(user, LANG);

        // Step 4: Safely build a fallback name from email
        if (name == null) {
            name = displayName;
            if (name == null && userEmail != null) {
                int atIndex = userEmail.indexOf("@");
                if (atIndex > 0) { // only valid if @ is found
                    name = userEmail.substring(0, atIndex);
                } else {
                    logger.warn("Invalid email format for user: {}", userEmail);
                    name = "User"; // fallback
                }
            }
        }

        // Step 5: Prepare user data map
        userMap.put(UID, uid);
        userMap.put(INUM_ATTR, inum);
        userMap.put("name", name);
        userMap.put("email", userEmail);
        userMap.put(DISPLAY_NAME, displayName);
        userMap.put(LAST_NAME, sn);
        userMap.put(LANG, lang);

        logger.info("Returning user data for email: {}", email);
        return userMap;
    }

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

            String subject = templateData.getOrDefault("subject", "Your Username Information");
            String htmlBody = templateData.get("body");

            if (htmlBody == null || htmlBody.isEmpty()) {
                logger.error("Email HTML body is empty for language: {}", preferredLang);
                return false;
            }

            // Plain text version
            String textBody = htmlBody.replaceAll("\\<.*?\\>", "");

            MailService mailService = CdiUtil.bean(MailService.class);

            boolean sent = mailService.sendMailSigned(
                smtpConfig.getFromEmailAddress(),
                smtpConfig.getFromName(),
                to,
                null,
                subject,
                textBody,
                htmlBody
            );

            if (sent) {
                logger.info("Username email sent successfully to {}", to);
            } else {
                logger.error("Failed to send username email to {}", to);
            }

            return sent;

        } catch (Exception e) {
            logger.error("Error sending username email: {}", e.getMessage(), e);
            return false;
        }
    }
}
