/*------------------------------------------------------------------------------
Name:      LoginQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: LoginQosWrapper.java,v 1.2 2000/06/14 13:54:04 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Vector;


/**
 * This class encapsulates the qos of a publish() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>publish</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;callback type='IOR'>
 *           IOR:10000010033200000099000010....
 *        &lt;/callback>
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class LoginQosWrapper extends QosWrapper
{
   private String ME = "LoginQosWrapper";

   // <callback type="IOR>IOR:000122200..."</callback>
   protected Vector addressVec = new Vector();

   /** PtP messages wanted? */
   protected boolean noPtP = false; // <noPtP />    <!-- Don't send me any PtP messages (prevents spamming) -->


   /**
    * Default constructor for clients without asynchronous callbacks.
    */
   public LoginQosWrapper()
   {
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
      sb.append("</qos>");

      return sb.toString();
   }
}
