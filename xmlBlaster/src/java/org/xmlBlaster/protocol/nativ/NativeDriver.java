/*------------------------------------------------------------------------------
Name:      NativeDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   NativeDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: NativeDriver.java,v 1.1 2000/06/26 11:59:53 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.nativ;

import org.jutils.log.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.client.LoginQosWrapper;



/**
 * Native driver class to invoke the xmlBlaster server in the same JVM (not remote). 
 * <p />
 * This is a demo fragment only. You can take a copy and
 * code your own native access.
 * <p />
 * The native driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 *   Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver,\
 *                    RMI:org.xmlBlaster.protocol.rmi.RmiDriver,\
 *                    NativeDemo:org.xmlBlaster.protocol.nativ.NativeDriver
 *
 *   Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\
 *                            RMI:org.xmlBlaster.protocol.rmi.CallbackRmiDriver,\
 *                            NativeDemo:org.xmlBlaster.protocol.nativ.CallbackNativeDriver
 * </pre>
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * @author ruff@swand.lake.de
 */
public class NativeDriver implements I_Driver
{
   private static final String ME = "NativeDriver";
   /** The singleton handle for this xmlBlaster server */
   private Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** The authentication session identifier */
   private String sessionId = null;


   /** Get a human readable name of this driver. 
    * <p />
    * Enforced by interface I_Driver.
    */
   public String getName()
   {
      return ME;
   }


   /**
    * Start xmlBlaster native access. 
    * <p />
    * Enforced by interface I_Driver.
    * @param args The command line parameters
    */
   public void init(String args[], Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;
      Log.info(ME, "Started successfully native driver.");

      // ------------------------------
      // login and get a session id ...
      String loginName = XmlBlasterProperty.get("NativeDemo.loginName", "NativeDemo");
      String passwd = XmlBlasterProperty.get("NativeDemo.password", "secret");
      // "NativeDemo" below is the 'callback protocol type', which results in instantiation of given the class:
      CallbackAddress callback = new CallbackAddress("NativeDemo", "org.xmlBlaster.protocol.nativ.CallbackNativeDriver");
      LoginQosWrapper loginQos = new LoginQosWrapper(callback);
      sessionId = login(loginName, passwd, loginQos.toXml());

      // ----------------------------------------------------
      // Sending demo message to our CallbackNativeDriver ...
      String receiverName = loginName;
      String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                      "</key>";
      String qos = "<qos>" +
                   "   <destination queryType='EXACT'>" +
                           receiverName +
                   "   </destination>" +
                   "</qos>";
      String content = "Hi " + receiverName + "!";
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), qos);
      try {
         String publishOid = xmlBlasterImpl.publish(sessionId, msgUnit);
         Log.info(ME, "Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
      }
   }


   /**
    * Instructs native driver to shut down. 
    * <p />
    * Enforced by interface I_Driver.
    */
   public void shutdown()
   {
      Log.info(ME, "Shutting down native driver ...");
   }


   /**
    * Command line usage. 
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "NativeDriver options:\n";
      //text += "   -native.name        Specify a logging name.\n";
      text += "\n";
      return text;
   }


   /**
    * Does a login, returns a valid session id. 
    * <p />
    * @param loginName The unique login name
    * @param password
    * @param qos_literal The login quality of service "<qos></qos>"
    * @return sessionId The unique ID for this client
    * @exception XmlBlasterException If user is unknown
    */
   private String login(String loginName, String password, String qos_literal) throws XmlBlasterException
   {
      String sessionId = null;
      if (loginName==null || password==null || qos_literal==null) {
         Log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException("LoginFailed.InvalidArguments", "login failed: please use no null arguments for login()");
      }

      try {
         String tmpSessionId = authenticate.login(loginName, password, qos_literal, sessionId);
         if (tmpSessionId == null || (sessionId != null && sessionId.length() > 2 && !tmpSessionId.equals(sessionId))) {
            Log.warning(ME+".AccessDenied", "Login for " + loginName + " failed.");
            throw new XmlBlasterException("LoginFailed.AccessDenied", "Sorry, access denied");
         }
         Log.info(ME, "login for '" + loginName + "' successful.");
         return tmpSessionId;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }
}
