/*------------------------------------------------------------------------------
Name:      StatusQosSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * Parsing xml QoS (quality of service) of return status. 
 * <p />
 * <pre>
 *  &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='yourMessageOid'/> <!-- PublishReturnQos and EraseReturnQos only -->
 *     &lt;subscribe id='_subId:1/> <!-- SubscribeReturnQos and UnSubscribeQos only -->
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.util.qos.StatusQosData
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author ruff@swand.lake.de
 */
public class StatusQosSaxFactory extends org.xmlBlaster.util.XmlQoSBase implements I_StatusQosFactory
{
   private String ME = "StatusQosSaxFactory";
   private final Global glob;
   private final LogChannel log;

   private  StatusQosData statusQosData;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private boolean inState = false;
   private boolean inSubscribe = false;
   private boolean inKey = false;

   /**
    * Can be used as singleton. 
    */
   public StatusQosSaxFactory(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("core");
   }

   /**
    * Parses the given xml Qos and returns a StatusQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized StatusQosData readObject(String xmlQos) throws XmlBlasterException {
      if (xmlQos == null) {
         xmlQos = "<qos/>";
      }

      statusQosData = new StatusQosData(glob, this, xmlQos);

      if (!isEmpty(xmlQos)) // if possible avoid expensive SAX parsing
         init(xmlQos);      // use SAX parser to parse it (is slow)

      return statusQosData;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (name.equalsIgnoreCase("state")) {
         if (!inQos)
            return;
         inState = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("id") ) {
                  statusQosData.setState(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("info") ) {
                  statusQosData.setStateInfo(attrs.getValue(i).trim());
               }
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("subscribe")) {
         if (!inQos)
            return;
         inSubscribe = true;
         if (attrs != null) {
            statusQosData.setSubscriptionId(attrs.getValue("id"));
         }
         return;
      }

      if (name.equalsIgnoreCase("key")) {
         if (!inQos)
            return;
         inKey = true;
         if (attrs != null) {
            statusQosData.setKeyOid(attrs.getValue("oid"));
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
      if (super.endElementBase(uri, localName, name) == true)
         return;

      if (name.equalsIgnoreCase("state")) {
         inState = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("subscribe")) {
         inSubscribe = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("key")) {
         inKey = false;
         character.setLength(0);
         return;
      }

      character.setLength(0); // reset data from unknown tags
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(StatusQosData statusQosData, String extraOffset) {
      return writeObject_(statusQosData, extraOffset);
   }

   public static final String writeObject_(StatusQosData statusQosData, String extraOffset) {
      StringBuffer sb = new StringBuffer(180);
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<qos>"); // <!-- SubscribeRetQos -->");
      if (statusQosData.getState() != null && statusQosData.getState().length() > 0) {
         sb.append(offset).append(" <state id='").append(statusQosData.getState());
         if (statusQosData.getStateInfo() != null)
            sb.append("' info='").append(statusQosData.getStateInfo());
         sb.append("'/>");
      }
      if (statusQosData.getSubscriptionId() != null)
         sb.append(offset).append(" <subscribe id='").append(statusQosData.getSubscriptionId()).append("'/>");
      if (statusQosData.getKeyOid() != null)
         sb.append(offset).append(" <key oid='").append(statusQosData.getKeyOid()).append("'/>");
      sb.append(offset).append("</qos>");

      if (sb.length() < 16)
         return "";  // minimal footprint

      return sb.toString();
   }

   /*
    * Convenience method for server side XML string creation
   public static final String writeObject_(String state, String stateInfo, String subscriptionId, String keyOid, String extraOffset) {
      StringBuffer sb = new StringBuffer(180);
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<qos>"); // <!-- SubscribeRetQos -->");
      if (state != null && state.length() > 0) {
         sb.append(offset).append(" <state id='").append(state);
         if (stateInfo != null)
            sb.append("' info='").append(stateInfo);
         sb.append("'/>");
      }
      if (subscriptionId != null)
         sb.append(offset).append(" <subscribe id='").append(subscriptionId).append("'/>");
      if (keyOid != null)
         sb.append(offset).append(" <key oid='").append(keyOid).append("'/>");
      sb.append(offset).append("</qos>");

      if (sb.length() < 16)
         return "";  // minimal footprint

      return sb.toString();
   }
    */

   /**
    * A human readable name of this factory
    * @return "StatusQosSaxFactory"
    */
   public String getName() {
      return "StatusQosSaxFactory";
   }
}
