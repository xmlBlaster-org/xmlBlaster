/*------------------------------------------------------------------------------
Name:      PublishRetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;

import org.xml.sax.Attributes;


/**
 * Handling the returned QoS (quality of service)
 * @deprecated  Use org.xmlBlaster.client.qos.PublishReturnQos
 */
public final class PublishRetQos implements I_RetQos
{
   private String ME = "PublishRetQos";
   private final LogChannel log;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private boolean inState = false;
   /** the state of the message */
   private String stateId = Constants.STATE_OK;
   private String stateInfo = null;

   private boolean inKey = false;
   private String oid = null;

   /**
    */
   public PublishRetQos(Global glob, String xmlQos_literal) throws XmlBlasterException {
      this.log = glob.getLog(null);
      if (log.CALL) log.call(ME, "Entering PublishRetQos() (a message arrived)");
      if (log.DUMP) log.dump(ME, "PublishRetQos: " + xmlQos_literal);
      
      // init(xmlQos_literal);  // SAX parser is too slow

      if (xmlQos_literal != null && xmlQos_literal.length() > 0) {
         setStateId(parseOurself(xmlQos_literal, "<state id="));
         setStateInfo(parseOurself(xmlQos_literal, "info="));
         oid = parseOurself(xmlQos_literal, "<key oid=");
      }
   }

   /**
    */
   public PublishRetQos(Global glob, String stateId, String stateInfo) {
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
    * @see org.xmlBlaster.util.enum.Constants
    */
   public final String getStateInfo() {
      return this.stateInfo;
   }

   private final void setStateInfo(String stateInfo) {
      this.stateInfo = stateInfo;
   }

   /**
    * Get the key oid of the published message
    */
   public final String getOid() {
      return this.oid;
   }

   public final void setOid(String oid) {
      this.oid = oid;
   }

   /*
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      super.startElement(uri, localName, name, attrs);

      //if (log.TRACE) log.trace(ME, "Entering startElement for " + name);

      if (name.equalsIgnoreCase("state")) {
         if (!inQos)
            return;
         inState = true;
         if (attrs != null) {
            String tmp = attrs.getValue("id");
            if (tmp == null)
               log.error(ME, "<qos><state id is missing");
            else
               this.setStateId(tmp);
            this.stateInfo = attrs.getValue("info");
         }
         return;
      }

      if (name.equalsIgnoreCase("key")) {
         if (!inQos)
            return;
         inKey = true;
         if (attrs != null) {
            this.oid = attrs.getValue("oid");
            if (this.oid == null)
               log.error(ME, "<qos><key oid is missing");
         }
         return;
      }
   }
    */

   /*
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
   public final void endElement(String uri, String localName, String name) {
      super.endElement(uri, localName, name);

      // if (log.TRACE) log.trace(ME, "Entering endElement for " + name);

      if(name.equalsIgnoreCase("state")) {
         inState = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("key")) {
         inKey = false;
         character.setLength(0);
         return;
      }
   }
    */

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
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<qos>"); // <!-- PublishRetQos -->");
      if (getStateId().length() > 0) {
         sb.append(offset).append("  <state id='").append(stateId);
         if (stateInfo != null)
            sb.append("' info='").append(stateInfo);
         sb.append("'/>");
      }
      if (getOid() != null)
         sb.append(offset).append("  <key oid='").append(getOid()).append("'/>");
      sb.append(offset).append("</qos>");

      return sb.toString();
   }
}
