/*------------------------------------------------------------------------------
Name:      LoginQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: LoginQosWrapper.java,v 1.17 2001/09/05 12:48:47 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import java.util.Vector;


/**
 * This class encapsulates the qos of a login() or connect(). 
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * Please see documentation of ConnectQos
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.ConnectQos
 * @deprecated Please use ConnectQos
 */
public class LoginQosWrapper extends ConnectQos
{
   private String ME = "LoginQosWrapper";

   public LoginQosWrapper()
   {
   }

   public LoginQosWrapper(String mechanism, String version, String loginName, String password) throws XmlBlasterException
   {
      super(mechanism,version,loginName,password);
   }

   public LoginQosWrapper(String mechanism, String version) throws XmlBlasterException
   {
      getPlugin(mechanism, version);
   }


   public LoginQosWrapper(I_SecurityQos securityQos)
   {
      this.securityQos = securityQos;
   }
}
