/*------------------------------------------------------------------------------
Name:      SubscribeRetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the returned QoS (quality of service)
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;


/**
 * Handling the returned QoS (quality of service) of a subscribe() call. 
 * <p />
 * If you are a Java client and use the XmlBlasterConnection helper class
 * you get this object as the subscribe() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='yourMessageOid'/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.SubscribeRetQosTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html" target="others">the interface.subscribe requirement</a>
 */
public final class SubscribeRetQos implements I_RetQos
{
   private String ME = "SubscribeRetQos";
   private final LogChannel log;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private boolean inState = false;
   /** the state of the message */
   private String stateId = Constants.STATE_OK;
   private String stateInfo = null;

   private boolean inSubscribe = false;
   private String subId = null;

   /**
    */
   public SubscribeRetQos(Global glob, String xmlQos_literal) throws XmlBlasterException {
      this.log = glob.getLog(null);
      if (log.CALL) log.call(ME, "Entering SubscribeRetQos() (a message arrived)");
      if (log.DUMP) log.dump(ME, "SubscribeRetQos: " + xmlQos_literal);
      
      if (xmlQos_literal != null && xmlQos_literal.length() > 0) {
         setStateId(parseOurself(xmlQos_literal, "<state id="));
         setStateInfo(parseOurself(xmlQos_literal, "info="));
         subId = parseOurself(xmlQos_literal, "<subscribe id=");
      }
   }

   /**
    */
   public SubscribeRetQos(Global glob, String stateId, String stateInfo) {
      this.log = glob.getLog(null);
      setStateId(stateId);
      setStateInfo(stateInfo);
   }

   /**
    * Parse xml ourself, to gain performance
    */
   private final String parseOurself(String str, String token) {
      int index = str.indexOf(token);
      if (index >= 0) {
         int from = index+token.length();
         char apo = str.charAt(from);
         int end = str.indexOf(apo, from+1);
         if (end > 0) {
            return str.substring(from+1, end);
         }
      }
      return null;
   }

   /**
    * Access the state of message. 
    * @return OK (Other values are not yet supported)
    */
   public final String getStateId() {
      return stateId;
   }

   private final void setStateId(String id) {
      if (id == null)
         this.stateId = Constants.STATE_OK;
      else
         this.stateId = id;
   }

   /**
    * Additional structured information about a state. 
    * @return "QUEUED" or "QUEUED[bilbo]"
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public final String getStateInfo() {
      return this.stateInfo;
   }

   private final void setStateInfo(String stateInfo) {
      this.stateInfo = stateInfo;
   }

   /**
    * Get the key oid of the subscribeed message
    */
   public final String getSubscriptionId() {
      return this.subId;
   }

   public final void setSubscribeId(String subId) {
      this.subId = subId;
   }

   /**
    * @see #toXml(String)
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    * @return The XML representation
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(180);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<qos>"); // <!-- SubscribeRetQos -->");
      if (getStateId().length() > 0) {
         sb.append(offset).append("   <state id='").append(stateId);
         if (stateInfo != null)
            sb.append(offset).append("' info='").append(stateInfo);
         sb.append("'/>");
      }
      if (getSubscriptionId() != null)
         sb.append(offset).append("   <subscribe id='").append(getSubscriptionId()).append("'/>");
      sb.append(offset).append("</qos>");

      return sb.toString();
   }
}
