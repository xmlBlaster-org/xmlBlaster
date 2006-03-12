/*------------------------------------------------------------------------------
Name:      StatusQosSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.RcvTimestamp;

import org.xml.sax.*;


/**
 * Parsing xml QoS (quality of service) of return status. 
 * <p />
 * <pre>
 *  &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *
 *     &lt;!-- PublishReturnQos and EraseReturnQos only -->
 *     &lt;key oid='yourMessageOid'/>
 *
 *     &lt;!-- SubscribeReturnQos and UnSubscribeQos only -->
 *     &lt;subscribe id='_subId:1/>
 *
 *     &lt;!-- UTC time when message was created in xmlBlaster server,
 *              in nanoseconds since 1970 -->
 *     &lt;rcvTimestamp nanos='1007764305862000002'>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.util.qos.StatusQosData
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public class StatusQosSaxFactory extends org.xmlBlaster.util.XmlQoSBase implements I_StatusQosFactory
{
   private final Global glob;
   private static Logger log = Logger.getLogger(StatusQosSaxFactory.class.getName());

   private  StatusQosData statusQosData;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   /*
   private boolean inState;
   private boolean inSubscribe;
   private boolean inKey;
   private boolean inRcvTimestamp;
   */

   /**
    * Can be used as singleton. 
    */
   public StatusQosSaxFactory(Global glob) {
      super(glob);
      this.glob = glob;

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

      //this.inState = false;
      //this.inSubscribe = false;
      //this.inKey = false;
      //this.inRcvTimestamp = false;
      
      statusQosData = new StatusQosData(glob, this, xmlQos, MethodName.UNKNOWN);

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
         //this.inState = true;
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

      if (name.equalsIgnoreCase(MethodName.SUBSCRIBE.getMethodName())) { // "subscribe"
         if (!inQos)
            return;
//       this.inSubscribe = true;
         if (attrs != null) {
            statusQosData.setSubscriptionId(attrs.getValue("id"));
         }
         return;
      }

      if (name.equalsIgnoreCase("key")) {
         if (!inQos)
            return;
//       this.inKey = true;
         if (attrs != null) {
            statusQosData.setKeyOid(attrs.getValue("oid"));
         }
         return;
      }
 
      if (name.equalsIgnoreCase("rcvTimestamp")) {
        if (!inQos)
           return;
        if (attrs != null) {
           int len = attrs.getLength();
           for (int i = 0; i < len; i++) {
              if( attrs.getQName(i).equalsIgnoreCase("nanos") ) {
                String tmp = attrs.getValue(i).trim();
                try { statusQosData.setRcvTimestamp(new RcvTimestamp(Long.parseLong(tmp))); } catch(NumberFormatException e) { log.severe("Invalid rcvTimestamp - nanos =" + tmp); };
              }
           }
        }
//      this.inRcvTimestamp = true;
        return;
     }

     if (name.equalsIgnoreCase("isErase")) {
        if (!inQos)
           return;
        statusQosData.setMethod(MethodName.ERASE);
        return;
     }
     if (name.equalsIgnoreCase("isPublish")) {
        if (!inQos)
           return;
        statusQosData.setMethod(MethodName.PUBLISH);
        return;
     }
     if (name.equalsIgnoreCase("isSubscribe")) {
        if (!inQos)
           return;
        statusQosData.setMethod(MethodName.SUBSCRIBE);
        return;
     }
     if (name.equalsIgnoreCase("isUnSubscribe")) {
        if (!inQos)
           return;
        statusQosData.setMethod(MethodName.UNSUBSCRIBE);
        return;
     }
     if (name.equalsIgnoreCase("isUpdate")) {
        if (!inQos)
           return;
        statusQosData.setMethod(MethodName.UPDATE);
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
//       this.inState = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase(MethodName.SUBSCRIBE.getMethodName())) { // "subscribe"
//       this.inSubscribe = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("key")) {
//       this.inKey = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("rcvTimestamp")) {
//       this.inRcvTimestamp = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("isErase")) {
         character.setLength(0);
         return;
      }
      if (name.equalsIgnoreCase("isPublish")) {
         character.setLength(0);
         return;
      }
      if (name.equalsIgnoreCase("isSubscribe")) {
         character.setLength(0);
         return;
      }
      if (name.equalsIgnoreCase("isUnSubscribe")) {
         character.setLength(0);
         return;
      }
      if (name.equalsIgnoreCase("isUpdate")) {
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
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<qos>"); // <!-- SubscribeRetQos -->");
      if (!statusQosData.isOk()) {
         sb.append(offset).append(" <state id='").append(statusQosData.getState());
         if (statusQosData.getStateInfo() != null)
            sb.append("' info='").append(statusQosData.getStateInfo());
         sb.append("'/>");
      }
      if (statusQosData.getSubscriptionId() != null)
         sb.append(offset).append(" <").append(MethodName.SUBSCRIBE.getMethodName()).append(" id='").append(statusQosData.getSubscriptionId()).append("'/>");
      if (statusQosData.getKeyOid() != null)
         sb.append(offset).append(" <key oid='").append(statusQosData.getKeyOid()).append("'/>");

      if (statusQosData.getRcvTimestamp() != null)
         sb.append(statusQosData.getRcvTimestamp().toXml(extraOffset+Constants.INDENT, false));

      if (statusQosData.getMethod() == MethodName.ERASE) {
         sb.append(offset).append("<isErase/>");
      }
      else if (statusQosData.getMethod() == MethodName.PUBLISH) {
         sb.append(offset).append("<isPublish/>");
      }
      else if (statusQosData.getMethod() == MethodName.SUBSCRIBE) {
         sb.append(offset).append("<isSubscribe/>");
      }
      else if (statusQosData.getMethod() == MethodName.UNSUBSCRIBE) {
         sb.append(offset).append("<isUnSubscribe/>");
      }
      else if (statusQosData.getMethod() == MethodName.UPDATE) {
         sb.append(offset).append("<isUpdate/>");
      }
     
      sb.append(offset).append("</qos>");

      if (sb.length() < 16)
         return "<qos/>";  // minimal footprint

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
