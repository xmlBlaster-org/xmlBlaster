/*------------------------------------------------------------------------------
Name:      LoginQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: LoginQosWrapper.java,v 1.9 2001/08/30 17:14:49 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.authentication.plugins.I_ClientHelper;
import org.xmlBlaster.authentication.plugins.I_InitQos;
import java.util.Vector;


/**
 * This class encapsulates the qos of a publish() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>login</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
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
 * A typical login sequence looks like this:
 * <pre>
 *   &lt;securityService type="simple" version="1.0">
 *      &lt;![CDATA[
 *         &lt;user>name&lt;/user>
 *         &lt;passwd>passwd&lt;/passwd>
 *      ]]>
 *   &lt;/securityService>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class LoginQosWrapper extends QosWrapper
{
   private String ME = "LoginQosWrapper";

   public static final int EXCLUDE_SECURITY = 1;
   public static final int DEFAULT = 0;

   // <callback type="IOR>IOR:000122200..."</callback>
   protected Vector addressVec = new Vector();

   /** PtP messages wanted? */
   protected boolean noPtP = false; // <noPtP />    <!-- Don't send me any PtP messages (prevents spamming) -->

   private PluginLoader pMgr;
   private I_ClientHelper plugin;

   /**
    * Default constructor for clients without asynchronous callbacks.
    */
   public LoginQosWrapper()
   {
   }

   private I_ClientHelper getPlugin()
   {
      if (plugin==null) {
         if (pMgr==null) pMgr=PluginLoader.getInstance();
         try {
            plugin=pMgr.getCurrentClientPlugin();
         }
         catch (Exception e) {
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

   public I_InitQos getSecurityInitQoSWrapper()
   {
      return getPlugin().getInitQoSWrapper();
   }

   public String getSecurityPluginType()
   {
      I_InitQos securityInitQos = getSecurityInitQoSWrapper();
      if (securityInitQos != null)
         return securityInitQos.getPluginType();
      return null;
   }

   public String getSecurityPluginVersion()
   {
      I_InitQos securityInitQos = getSecurityInitQoSWrapper();
      if (securityInitQos != null)
         return securityInitQos.getPluginVersion();
      return null;
   }

   public String getUserId()
   {
      I_InitQos i=getSecurityInitQoSWrapper();
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
      return toXml(LoginQosWrapper.DEFAULT);
   }

   public final String toXml(int opt)
   {
      return toXml((String)null, opt);
   }

   public final String toXml(String extraOffset)
   {
      return toXml((String)null, EXCLUDE_SECURITY);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset, int opt)
   {
      I_InitQos secInitQoSWrapper = null;

      if(plugin!=null) {
         secInitQoSWrapper = getPlugin().getInitQoSWrapper();
      }
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append("<qos>\n");
      if (noPtP)
         sb.append(offset + "   <noPtP />");
      for (int ii=0; ii<addressVec.size(); ii++) {
         CallbackAddress ad = (CallbackAddress)addressVec.elementAt(ii);
         sb.append(ad.toXml("   ")).append("\n");
      }
      if(opt!=EXCLUDE_SECURITY) {
         // For example DefaultClientInitQoSWrapper with <securityService ...
         if(secInitQoSWrapper!=null) sb.append(secInitQoSWrapper.toXml(offset)); // includes the qos of the ClientSecurityHelper
      }
      sb.append("</qos>");

      return sb.toString();
   }

}
