package org.xmlBlaster.authentication.plugins;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * A client helper.
 *
 * The reason is for clients to access xmlBlaster
 * transparently from the authentication method
 * <p />
 * Here is a typical example for a password based QoS
 * <pre>
 *  &lt;qos>
 *     &lt;securityService type='htpasswd' version='1.0'>
 *        &lt;![CDATA[
 *           &lt;user>michele&lt;/user>
 *           &lt;passwd>secret&lt;/passwd>
 *        ]]>
 *     &lt;/securityService>
 *  &lt;/qos>
 * </pre>
 */
public interface I_SecurityQos {

   /**
    * Parse the given xml string which contains the userId and credentials. 
    * Should be able to parse with or without surrounding &lt;security> tag
    */
   public void parse(String xml) throws XmlBlasterException;

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

   public String getPluginType();

   public String getPluginVersion();

   /**
    * Serialize the information.
    */
   public String toXml(String extraOffset);

}
