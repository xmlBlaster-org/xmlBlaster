/*------------------------------------------------------------------------------
Name:      UpdateQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: UpdateQoS.java,v 1.17 2001/09/30 13:49:22 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xml.sax.Attributes;


/**
 * QoS (quality of service) informations sent from server to client<br />
 * via the update() method from the BlasterCallback interface.
 * <p />
 * If you are a Java client you may use this class to parse the QoS argument.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos> &lt;!-- UpdateQoS -->
 *     &lt;state>
 *        OK
 *     &lt;/state>
 *     &lt;sender>
 *        Tim
 *     &lt;/sender>
 *     &lt;subscriptionId>
 *        __sys__TotalMem
 *     &lt;/subscriptionId>
 *     &lt;queue index='0' of='1'> &lt;!-- If queued messages are flushed on login -->
 *     &lt;/queue>
 *  &lt;/qos>
 * </pre>
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

   private int queueIndex = -1;
   private int queueSize = -1;
   private boolean inQueue = false;


   /**
    * Constructs the specialized quality of service object for a update() call.
    */
   public UpdateQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Creating UpdateQoS(" + xmlQoS_literal + ")");
      //if (Log.CALL) Log.call(ME, "Creating UpdateQoS()");
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
    * If durable messages where in queue, this is flushed on login. 
    * @return The number of queued messages
    */
   public int getQueueSize()
   {
      return queueSize;
   }

   /**
    * If durable messages where in queue, this is flushed on login. 
    * @return The index of the message of the queue
    */
   public int getQueueIndex()
   {
      return queueIndex;
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

      //if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (name.equalsIgnoreCase("state")) {
         if (!inQos)
            return;
         inState = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <state> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found state tag");
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
               Log.warn(ME, "Ignoring sent <sender> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found sender tag");
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
               Log.warn(ME, "Ignoring sent <subscriptionId> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found subscriptionId tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         if (!inQos)
            return;
         inQueue = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("index") ) {
                 String tmp = attrs.getValue(i).trim();
                 try { queueIndex = Integer.parseInt(tmp); } catch(NumberFormatException e) { Log.error(ME, "Invalid queue - index =" + tmp); };
               }
               if( attrs.getQName(i).equalsIgnoreCase("size") ) {
                 String tmp = attrs.getValue(i).trim();
                 try { queueSize = Integer.parseInt(tmp); } catch(NumberFormatException e) { Log.error(ME, "Invalid queue - index =" + tmp); };
               }
            }
            // if (Log.TRACE) Log.trace(ME, "Found queue tag");
         }
         return;
      }

   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name)
   {
      super.endElement(uri, localName, name);

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

      if(name.equalsIgnoreCase("queue")) {
         inQueue = false;
         character.setLength(0);
         return;
      }

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
      if(getQueueSize() > 0) {
         sb.append(offset + "   <queue index='"+getQueueIndex()+"' size='"+getQueueSize()+"'");
         sb.append(offset + "   </queue>");
      }


      sb.append(offset + "</qos>\n");

      return sb.toString();
   }


   public final String toString()
   {
      return toXml(null);
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
                   "   <queue index='0' size='1'>\n" +
                   "   </queue>\n" +
                   "</qos>";

      UpdateQoS up = new UpdateQoS(xml);
      Log.info("Test", "#1\n" + up.toXml());

      up = new UpdateQoS(up.toXml());
      Log.exit("Test", "#2\n" + up.toXml());
   }
}
