/*------------------------------------------------------------------------------
Name:      UpdateQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: UpdateQoS.java,v 1.9 2000/03/28 10:26:42 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xml.sax.AttributeList;


/**
 * QoS (quality of service) informations sent from server to client<br />
 * via the update() method from the BlasterCallback interface.
 * <p />
 * If you are a Java client you may use this class to parse the QoS argument.
 */
public class UpdateQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "UpdateQoS";

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private boolean inState = false;
   /** the state of the message */
   private String state = null;
   /** helper flag for SAX parsing: parsing inside <sender> ? */
   private boolean inSender = false;
   /** the sender (publisher) of this message (unique loginName) */
   private String sender = null;
   /** helper flag for SAX parsing: parsing inside <subscriptionId> ? */
   private boolean inSubscriptionId = false;
   /** If Pub/Sub style update: contains the subscribe ID which caused this update */
   private String subscriptionId = null;


   /**
    * Constructs the specialized quality of service object for a update() call.
    */
   public UpdateQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Creating UpdateQoS(" + xmlQoS_literal + ")");
      //if (Log.CALLS) Log.calls(ME, "Creating UpdateQoS()");
      init(xmlQoS_literal);
   }


   /**
    * Access sender name.
    * @return loginName of sender
    */
   public String getSender()
   {
      return sender;
   }


   /**
    * Access state of message.
    * @return OK (Other values are not yet supported)
    */
   public String getState()
   {
      return state;
   }


   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   public String getSubscriptionId()
   {
      return subscriptionId;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String name, AttributeList attrs)
   {
      super.startElement(name, attrs);

      //if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (name.equalsIgnoreCase("state")) {
         if (!inQos)
            return;
         inState = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warning(ME, "Ignoring sent <state> attribute " + attrs.getName(i) + "=" + attrs.getValue(i).trim());
            }
            if (Log.TRACE) Log.trace(ME, "Found state tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("sender")) {
         if (!inQos)
            return;
         inSender = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warning(ME, "Ignoring sent <sender> attribute " + attrs.getName(i) + "=" + attrs.getValue(i).trim());
            }
            if (Log.TRACE) Log.trace(ME, "Found sender tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("subscriptionId")) {
         if (!inQos)
            return;
         inSubscriptionId = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warning(ME, "Ignoring sent <subscriptionId> attribute " + attrs.getName(i) + "=" + attrs.getValue(i).trim());
            }
            if (Log.TRACE) Log.trace(ME, "Found subscriptionId tag");
         }
         return;
      }
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String name)
   {
      super.endElement(name);

      // if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);

      if(name.equalsIgnoreCase("state")) {
         inState = false;
         state = character.toString().trim();
         // if (Log.TRACE) Log.trace(ME, "Found message state = " + state);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("sender")) {
         inSender = false;
         sender = character.toString().trim();
         // if (Log.TRACE) Log.trace(ME, "Found message sender login name = " + sender);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("subscriptionId")) {
         inSubscriptionId = false;
         subscriptionId = character.toString().trim();
         // if (Log.TRACE) Log.trace(ME, "Found message subscriptionId = " + subscriptionId);
         character.setLength(0);
         return;
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn()
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<qos> <!-- UpdateQoS -->");
      if (state != null) {
         sb.append(offset + "   <state>");
         sb.append(offset + "      " + state);
         sb.append(offset + "   </state>");
      }
      if (sender != null) {
         sb.append(offset + "   <sender>");
         sb.append(offset + "      " + sender);
         sb.append(offset + "   </sender>");
      }
      if (subscriptionId != null) {
         sb.append(offset + "   <subscriptionId>");
         sb.append(offset + "      " + subscriptionId);
         sb.append(offset + "   </subscriptionId>");
      }
      sb.append(offset + "</qos>\n");

      return sb;
   }


   public final String toString()
   {
      return printOn(null).toString();
   }


   /**
    *  For testing invoke: jaco org.xmlBlaster.client.UpdateQoS
    */
   public static void main( String[] args ) throws XmlBlasterException
   {
      String xml = "<qos>\n" +
                   "   <state>\n" +
                   "      OK\n" +
                   "   </state>\n" +
                   "   <sender>\n" +
                   "      Joe\n" +
                   "   </sender>\n" +
                   "   <subscriptionId>\n" +
                   "      1234567890\n" +
                   "   </subscriptionId>\n" +
                   "</qos>";

      UpdateQoS up = new UpdateQoS(xml);
      Log.info("Test", "\n" + up.toXml());

      up = new UpdateQoS(up.toXml());
      Log.exit("Test", "\n" + up.toXml());
   }
}
