package org.gluu.agama.usernameclass;

import java.util.HashMap;
import java.util.Map;
import org.gluu.agama.forgetusername.JansForgetUsername;

public abstract class UsernameResendclass {

    public abstract Map<String, String> getUserEntityByMail(String email);

    public abstract boolean sendUsernameEmail(String to, String username, String lang);

    // New methods for OTP via Twilio
    public abstract String sendOtpToPhone(String phone, String lang);

    public abstract boolean validateOTPCode(String phone, String code);

    
    public static UsernameResendclass getInstance(HashMap config) {
        return new JansForgetUsername(config);
    }

}
