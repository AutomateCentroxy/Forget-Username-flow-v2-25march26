package org.gluu.agama.smtp;

import java.util.Map;

class EmailUsernameEs {

    static Map<String, String> get(String username) {

        String html = """
<table role="presentation" cellspacing="0" cellpadding="0" width="100%" style="background-color:#F2F4F6;margin:0;padding:0;width:100%;">
  <tbody>
    <tr>
      <td align="center">
        <table role="presentation" cellspacing="0" cellpadding="0" width="100%" style="margin:0;padding:0;">
          <tbody>
            <tr>
              <td align="center" style="padding:25px 0;text-align:center;">
                <img src="https://storage.googleapis.com/email_template_staticfiles/Phi_logo320x132_Aug2024.png" width="160" alt="Phi Logo" style="border:none;">
              </td>
            </tr>

            <tr>
              <td style="width:100%;margin:0;padding:0;">
                <table role="presentation" cellspacing="0" cellpadding="0" width="570" align="center" style="background-color:#FFFFFF;margin:0 auto;padding:0;border-radius:4px;">
                  <tbody>
                    <tr>
                      <td style="padding:45px;font-family:'Nunito Sans',Helvetica,Arial,sans-serif;color:#51545E;font-size:16px;line-height:1.625;">
                        <p>Hola,</p>
                        <p>Tal como solicitaste, aquí tienes el nombre de usuario asociado a esta dirección de correo electrónico:</p>

                        <p><strong>Nombre de usuario:</strong></p>
                        <div style="text-align:center;margin:30px 0;">
                          <div style="display:inline-block;background-color:#f5f5f5;color:#AD9269;font-size:28px;font-weight:600;letter-spacing:2px;padding:10px 20px;border-radius:4px;">
                            """ + username + """
                          </div>
                        </div>

                        <p>Si no realizaste esta solicitud, por favor contacta de inmediato con nuestro equipo de soporte a través de 
                        <a href="mailto:support@phiwallet.com" style="color:#AD9269;text-decoration:none;">support@phiwallet.com</a>
                         o por chat en vivo en 
                        <a href="https://www.phiwallet.com" style="color:#AD9269;text-decoration:none;">www.phiwallet.com</a>.
                        </p>

                        <p><strong>Por tu seguridad:</strong></p>
                        <ul><li>Nunca compartas tu nombre de usuario ni tu contraseña.</li>
                            <li>Phi Wallet nunca te pedirá información confidencial como tu contraseña.</li>
                        </ul>

                        <p>Gracias por elegir Phi Wallet.</p>
                        <p style="margin-top:30px;">Saludos,<br>Equipo Phi Wallet</p>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>

            <tr>
              <td>
                <table role="presentation" cellspacing="0" cellpadding="0" width="570" align="center" style="margin:0 auto;padding:0;text-align:center;">
                  <tbody>
                    <tr>
                      <td style="padding:20px;font-size:12px;color:#666;">
                        <p style="margin:0 0 10px 0;font-size:14px;font-weight:bold;color:#565555;">Síguenos en:</p>
                        <p>
                          <a href="https://www.facebook.com/PhiWallet"><img src="https://storage.googleapis.com/mwapp_prod_bucket/social_icon_images/facebook.png" style="height:20px;margin:0 5px;"></a>
                          <a href="https://x.com/PhiWallet"><img src="https://storage.googleapis.com/mwapp_prod_bucket/social_icon_images/twitter.png" style="height:20px;margin:0 5px;"></a>
                          <a href="https://www.instagram.com/phi.wallet"><img src="https://storage.googleapis.com/mwapp_prod_bucket/social_icon_images/instagram.png" style="height:20px;margin:0 5px;"></a>
                          <a href="https://www.linkedin.com/company/phiwallet"><img src="https://storage.googleapis.com/mwapp_prod_bucket/social_icon_images/linkedin.png" style="height:20px;margin:0 5px;"></a>
                        </p>
                        <p style="margin-top:10px;color:#A8AAAF;font-size:12px;">
                          Phi Wallet Unipessoal LDA<br>
                          Avenida da Liberdade 262 R/C<br>
                          1250-149 Lisboa, Portugal
                        </p>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </td>
            </tr>

          </tbody>
        </table>
      </td>
    </tr>
  </tbody>
</table>
""";

        return Map.of(
            "subject", "Recuperación de tu nombre de usuario de Phi Wallet",
            "body", html
        );
    }
}
