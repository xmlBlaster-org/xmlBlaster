/*------------------------------------------------------------------------------
Name:      LoginQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: LoginQosWrapper.java,v 1.15 2001/09/05 10:05:32 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.authentication.plugins.I_ClientHelper;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import java.util.Vector;


/**
 * This class encapsulates the qos of a login() or connect(). 
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>login</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;securityService type="simple" version="1.0">
 *          &lt;![CDATA[
 *          &lt;user>michele&lt;/user>
 *          &lt;passwd>secret&lt;/passwd>
 *          ]]>
 *        &lt;/securityService>
 *        &lt;session timeout='3600000' maxSessions='20'>
 *        &lt;/session>
 *        &lt;noPtP />
 *        &lt;callback type='IOR'>
 *           IOR:10000010033200000099000010....
 *           &lt;burstMode collectTime='400' />
 *        &lt;/callback>
 *     &lt;/qos>
 * </pre>
 * NOTE: As a user of the Java client helper classes (client.protocol.XmlBlasterConnection)
 * you don't need to create the <pre>&lt;callback></pre> element.
 * This is generated automatically from the XmlBlasterConnection class when instantiating
 * the callback driver.
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.ConnectQos
 */
public class LoginQosWrapper extends ConnectQos
{
   private String ME = "LoginQosWrapper";

   /**
    * Default constructor for clients without asynchronous callbacks
    * and default security plugin (as specified in xmlBlaster.properties)
    */
   public LoginQosWrapper()
   {
   }

   /**
    * Constructor for simple access with login name and password. 
    * @param mechanism may be null to use the default security plugin
    *                  as specified in xmlBlaster.properties
    * @param version may be null to use the default
    */
   public LoginQosWrapper(String mechanism, String version, String loginName, String password) throws XmlBlasterException
   {
      super(mechanism,version,loginName,password);
   }

   /**
    * For clients who whish to use the given security plugin. 
    * @param String The type of the plugin, e.g. "a2Blaster"
    * @param String The version of the plugin, e.g. "1.0"
    */
   public LoginQosWrapper(String mechanism, String version) throws XmlBlasterException
   {
      getPlugin(mechanism, version);
   }


   /**
    * Allows to specify how you want to identify yourself. 
    * <p />
    * Usage to login to xmlBlaster:
    * <pre>
    *    import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
    *    ...
    *    LoginQosWrapper qos = new LoginQosWrapper(new SecurityQos("joe", "secret"));
    *    xmlBlasterConnection.connect(qos);
    * </pre>
    */
   public LoginQosWrapper(I_SecurityQos securityQos)
   {
      this.securityQos = securityQos;
   }
}
