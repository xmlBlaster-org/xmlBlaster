/*------------------------------------------------------------------------------
Name:      LoginQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: LoginQosWrapper.java,v 1.14 2001/09/04 17:25:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PluginLoader;
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
 * @see org.xmlBlaster.authentication.ClientQoS
 */
public class LoginQosWrapper extends QosWrapper
{
   private String ME = "LoginQosWrapper";

   // <callback type="IOR>IOR:000122200..."</callback>
   protected Vector addressVec = new Vector();

   /** PtP messages wanted? */
   protected boolean noPtP = false; // <noPtP />    <!-- Don't send me any PtP messages (prevents spamming) -->

   /** Default session span of life is one hour */
   protected long sessionTimeout = 3600L * 1000L; // One hour
   /** Maximum of six parallel logins for the same client */
   protected int maxSessions = 6;
   /** Passing own sessionId is not yet supported */
   protected String sessionId = null;

   private PluginLoader pMgr;
   private I_ClientHelper plugin;
   private I_SecurityQos securityQos;

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
      securityQos = getPlugin(mechanism,version).getSecurityQos();
      securityQos.setUserId(loginName);
      securityQos.setCredential(password);
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


   /**
    * @param mechanism If null, the current plugin is used
    */
   private I_ClientHelper getPlugin(String mechanism, String version) throws XmlBlasterException
   {
      if (plugin==null) {
         if (pMgr==null) pMgr=PluginLoader.getInstance();
         try {
            if (mechanism != null)
               plugin=pMgr.getClientPlugin(mechanism, version);
            else
               plugin=pMgr.getCurrentClientPlugin();
         }
         catch (XmlBlasterException e) {
            Log.error(ME+".LoginQosWrapper", "Security plugin initialization failed. Reason: "+e.toString());
            Log.error(ME+".LoginQosWrapper", "No plugin. Trying to continue without the plugin.");
         }
      }

      return plugin;
   }

   /**
    * Default constructor for transient PtP messages.
    * <p />
    * To make the message persistent, use the
    * @param callback The object containing the callback address.<br />
    *        To add more callbacks, us the addCallbackAddress() method.
    */
   public LoginQosWrapper(CallbackAddress callback)
   {
      addCallbackAddress(callback);
   }


   /**
    * @param noPtP You are allowing to receive PtP messages?
    */
   public LoginQosWrapper(boolean noPtP)
   {
      this.noPtP = noPtP;
   }


   /**
    * Allows to specify how you want to identify yourself. 
    * <p />
    * Usage to login to xmlBlaster:
    * <pre>
    *    import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
    *    ...
    *    LoginQosWrapper qos = new LoginQosWrapper();
    *    qos.setCredential(new SecurityQos("joe", "secret"));
    *    xmlBlasterConnection.connect(qos);
    * </pre>
    */
   public void setSecurityQos(I_SecurityQos securityQos)
   {
      this.securityQos = securityQos;
   }


   /**
    * Allows to set session specific informations. 
    *
    * @param timeout The login session will be destroyed after given milliseconds
    * @param maxSessions The client wishes to establish this maximum of sessions in parallel
    */
   public void setSessionData(long timeout, int maxSessions)
   {
      this.sessionTimeout = timeout;
      this.maxSessions = maxSessions;
   }


   /**
    * Allow to receive Point to Point messages (default).
    */
   public void allowPtP()
   {
      this.noPtP = true;
   }


   /**
    * I don't want to receive any PtP messages.
    */
   public void disallowPtP()
   {
      this.noPtP = true;
   }


   /**
    * Add a callback address where to send the message.
    * <p />
    * Note you can invoke this multiple times to allow multiple callbacks.
    * @param callback  An object containing the protocol (e.g. EMAIL) and the address (e.g. hugo@welfare.org)
    */
   public void addCallbackAddress(CallbackAddress callback)
   {
      if (addressVec == null)
         addressVec = new Vector();
      addressVec.addElement(callback);
   }

   public I_SecurityQos getSecurityQos() throws XmlBlasterException
   {
      return this.securityQos;
   }

   public String getSecurityPluginType() throws XmlBlasterException
   {
      I_SecurityQos securityQos = getSecurityQos();
      if (securityQos != null)
         return securityQos.getPluginType();
      return null;
   }

   public String getSecurityPluginVersion() throws XmlBlasterException
   {
      I_SecurityQos securityQos = getSecurityQos();
      if (securityQos != null)
         return securityQos.getPluginVersion();
      return null;
   }

   public String getUserId() throws XmlBlasterException
   {
      I_SecurityQos i=getSecurityQos();
      if (i==null)
         return "NoLoginName";
      else
         return i.getUserId();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString()
   {
      return toXml();
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * The default is to include the security string
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      if(plugin!=null && securityQos==null) {
         try {
            securityQos = getPlugin(null,null).getSecurityQos();
         } catch(XmlBlasterException e) {
            Log.warn(ME+".toXml", e.toString());
         }
      }
      StringBuffer sb = new StringBuffer(160);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append("<qos>\n");

      // For example DefaultClientInitQoSWrapper with <securityService ...
      if(securityQos!=null) sb.append(securityQos.toXml(offset)); // includes the qos of the ClientSecurityHelper

      if (noPtP)
         sb.append(offset + "   <noPtP />");

      sb.append(offset).append("   <session timeout='").append(sessionTimeout).append("' maxSessions='").append(maxSessions).append("'>");
      if(sessionId!=null) {
         sb.append(offset).append("      <sessionId>").append(sessionId).append("</sessionId>");
      }
      sb.append(offset).append("   </session>");

      for (int ii=0; ii<addressVec.size(); ii++) {
         CallbackAddress ad = (CallbackAddress)addressVec.elementAt(ii);
         sb.append(ad.toXml("   ")).append("\n");
      }
      sb.append("</qos>");

      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.client.LoginQosWrapper */
   public static void main(String[] args)
   {
      try {
         org.xmlBlaster.util.XmlBlasterProperty.init(args);
         LoginQosWrapper qos = new LoginQosWrapper(new CallbackAddress("IOR"));
         I_SecurityQos securityQos = new org.xmlBlaster.authentication.plugins.simple.SecurityQos("joe", "secret");
         qos.setSecurityQos(securityQos);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
