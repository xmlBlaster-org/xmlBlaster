/*------------------------------------------------------------------------------
Name:      PublishRetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the returned QoS (quality of service)
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;

import org.xml.sax.Attributes;


/**
 * Handling the returned QoS (quality of service) of a publish() call. 
 * <p />
 * If you are a Java client and use the XmlBlasterConnection helper class
 * you get this object as the publish() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='yourMessageOid'/>
 *  &lt;/qos>
 * </pre>
 * @see classtest.PublishRetQosTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html" target="others">the interface.publish requirement</a>
 */
public class PublishRetQos extends org.xmlBlaster.util.XmlQoSBase implements I_RetQos
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
   public PublishRetQos(Global glob, String xmlQoS_literal) throws XmlBlasterException
   {
      this.log = glob.getLog(null);
      if (log.CALL) log.call(ME, "Entering PublishRetQos() (a message arrived)");
      if (log.DUMP) log.dump(ME, "PublishRetQos: " + xmlQoS_literal);
      init(xmlQoS_literal);
   }

   /**
    * Access the state of message. 
    * @return OK (Other values are not yet supported)
    */
   public String getStateId() {
      return stateId;
   }

   /**
    * Additional structured information about a state. 
    * @return "QUEUED" or "QUEUED[bilbo]"
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public String getStateInfo() {
      return this.stateInfo;
   }

   /**
    * Get the key oid of the published message
    */
   public final String getOid() {
      return this.oid;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
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
               this.stateId = tmp;
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

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
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
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<qos>"); // <!-- PublishRetQos -->");
      if (stateId != null) {
         sb.append(offset).append("   <state id='").append(stateId);
         if (stateInfo != null)
            sb.append(offset).append("' info='").append(stateInfo);
         sb.append("'/>");
      }
      if (getOid() != null)
         sb.append(offset).append("   <key oid='").append(getOid()).append("'/>");
      sb.append(offset).append("</qos>");

      return sb.toString();
   }
}
