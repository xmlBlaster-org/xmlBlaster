package org.xmlBlaster.authentication.plugins;

/**
 * A client helper.
 *
 * The reason is for clients to access xmlBlaster
 * transparently from the authentication method
 */
public interface I_SecurityInitQoSWrapper {

   /**
    * Set the userId for the login.
    * <p/>
    * @param String userId
    */
   public void setUserId(String userId);

   /**
    * Get the userId, which is used for the login;
    */
   public String getUserId();

   /**
    * Set the credential (password etc.).
    * <p/>
    * @param String credential
    */
   public void setCredential(String cred);

   public String getSecurityPluginType();

   public String getSecurityPluginVersion();

   /**
    * Serialize the information.
    */
   public String toXml(String extraOffset);

}
