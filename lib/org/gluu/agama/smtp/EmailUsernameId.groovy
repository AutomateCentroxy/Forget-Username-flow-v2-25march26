  package org.gluu.agama.smtp;

  import java.util.Map;

  class EmailUsernameId {

      static Map<String, String> get(String username) {

          String html = """
  <table role="presentation" cellspacing="0" cellpadding="0" width="100%" style="background-color:#F2F4F6;">
    <tbody>
      <tr>
        <td align="center">
          <table role="presentation" cellspacing="0" cellpadding="0" width="570" align="center" style="background-color:#FFFFFF;border-radius:4px;margin:20px 0;">
            <tbody>
              <tr>
                <td align="center" style="padding:25px 0;">
                  <img src="https://storage.googleapis.com/email_template_staticfiles/Phi_logo320x132_Aug2024.png" width="160" alt="Phi Logo">
                </td>
              </tr>
              <tr>
                <td style="padding:45px;font-family:'Nunito Sans',Helvetica,Arial,sans-serif;font-size:16px;color:#51545E;line-height:1.625;">
                  <p>Hai,</p>
                  <p>Sesuai permintaan Anda, berikut adalah nama pengguna yang terhubung dengan alamat email ini:</p>

                  <p><strong>Nama Pengguna:</strong></p>
                  <div style="text-align:center;margin:30px 0;">
                    <div style="display:inline-block;background-color:#f5f5f5;color:#AD9269;font-size:28px;font-weight:600;letter-spacing:2px;padding:10px 20px;border-radius:4px;">
                      """ + username + """
                    </div>
                  </div>

                  <p>Jika Anda tidak merasa melakukan permintaan ini, segera hubungi tim dukungan kami melalui 
                   <a href="mailto:support@phiwallet.com" style="color:#AD9269;text-decoration:none;">support@phiwallet.com</a>
                    atau melalui live chat di 
                    <a href="https://www.phiwallet.com" style="color:#AD9269;text-decoration:none;">www.phiwallet.com</a>
.
                  </p>

                  <p><strong>Demi keamanan Anda:</strong></p>

                  <ul>
                      <li>Jangan pernah membagikan nama pengguna atau kata sandi Anda.</li>
                      <li>Phi Wallet tidak akan pernah meminta informasi sensitif seperti kata sandi Anda.</li>
                  </ul>


                  <p>Terima kasih telah menggunakan Phi Wallet.</p>
                  <p style="margin-top:30px;">Salam hangat,<br>Tim Phi Wallet</p>
                </td>
              </tr>
            </tbody>
          </table>

          <table role="presentation" width="570" align="center" style="text-align:center;">
            <tbody>
              <tr>
                <td style="padding:20px;font-size:12px;color:#666;">
                  <p style="font-size:14px;font-weight:bold;color:#565555;">Ikuti kami di:</p>
                  <p>
                    <a href="https://www.facebook.com/PhiWallet"><img src="https://storage.googleapis.com/mwapp_prod_bucket/social_icon_images/facebook.png" style="height:20px;margin:0 5px;"></a>
                    <a href="https://x.com/PhiWallet"><img src="https://storage.googleapis.com/mwapp_prod_bucket/social_icon_images/twitter.png" style="height:20px;margin:0 5px;"></a>
                    <a href="https://www.instagram.com/phi.wallet"><img src="https://storage.googleapis.com/mwapp_prod_bucket/social_icon_images/instagram.png" style="height:20px;margin:0 5px;"></a>
                    <a href="https://www.linkedin.com/company/phiwallet"><img src="https://storage.googleapis.com/mwapp_prod_bucket/social_icon_images/linkedin.png" style="height:20px;margin:0 5px;"></a>
                  </p>
                  <p style="margin-top:10px;color:#A8AAAF;">
                    Phi Wallet Unipessoal LDA<br>
                    Avenida da Liberdade 262 R/C<br>
                    1250-149 Lisbon, Portugal
                  </p>
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
              "subject", "Pemulihan Nama Pengguna Phi Wallet Anda",
              "body", html
          );
      }
  }
