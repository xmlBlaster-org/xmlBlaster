/*------------------------------------------------------------------------------
Name:      UpdateQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: UpdateQoS.java,v 1.18 2001/12/07 23:53:19 ruff Exp $
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
 *     &lt;rcvTimestamp millis='1007764305862'> &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call -->
 *           2001-12-07 23:31:45.862   &lt;!-- The millis from above but human readable -->
 *     &lt;/rcvTimestamp>
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
   /** helper flag for SAX parsing: parsing inside <rcvTimestamp> ? */
   private boolean inRcvTimestamp = false;
   private long rcvTimestamp = 0L;
   private int rcvNanos = 0;
   private String rcvTime = null;

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
    * The approximate receive timestamp (UTC time),
    * when message was created - arrived at xmlBlaster server.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   public final long getRcvTimestamp()
   {
      return rcvTimestamp;
   }

   /**
    * Human readable form of message receive time in xmlBlaster server,
    * in SQL representation e.g.:<br />
    * 2001-12-07 23:31:45.862
    */
   public final String getRcvTime()
   {
      return rcvTime;
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

      if (name.equalsIgnoreCase("rcvTimestamp")) {
         if (!inQos)
            return;
         inRcvTimestamp = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("millis") ) {
                 String tmp = attrs.getValue(i).trim();
                 try { rcvTimestamp = Long.parseLong(tmp); } catch(NumberFormatException e) { Log.error(ME, "Invalid rcvTimestamp - millis =" + tmp); };
               }
               else if( attrs.getQName(i).equalsIgnoreCase("nanos") ) {
                 String tmp = attrs.getValue(i).trim();
                 try { rcvNanos = Integer.parseInt(tmp); } catch(NumberFormatException e) { Log.error(ME, "Invalid rcvTimestamp - nanos =" + tmp); };
               }
            }
            // if (Log.TRACE) Log.trace(ME, "Found rcvTimestamp tag");
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

      if(name.equalsIgnoreCase("rcvTimestamp")) {
         inRcvTimestamp = false;
         rcvTime = character.toString().trim();
         // if (Log.TRACE) Log.trace(ME, "Found message rcvTimestamp = " + rcvTimestamp);
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

      sb.append(offset).append("<qos> <!-- UpdateQoS -->");
      if (state != null) {
         sb.append(offset).append("   <state>");
         sb.append(offset).append("      ").append(state);
         sb.append(offset).append("   </state>");
      }
      if (sender != null) {
         sb.append(offset).append("   <sender>");
         sb.append(offset).append("      ").append(sender);
         sb.append(offset).append("   </sender>");
      }
      if (subscriptionId != null) {
         sb.append(offset).append("   <subscriptionId>");
         sb.append(offset).append("      ").append(subscriptionId);
         sb.append(offset).append("   </subscriptionId>");
      }

      sb.append(offset).append("   <rcvTimestamp millis='").append(getRcvTimestamp()).append("' nanos='").append(rcvNanos).append("'>");
      sb.append(offset).append("      ").append(getRcvTime());
      sb.append(offset).append("   </rcvTimestamp>");

      if(getQueueSize() > 0) {
         sb.append(offset).append("   <queue index='"+getQueueIndex()+"' size='"+getQueueSize()+"'");
         sb.append(offset).append("   </queue>");
      }


      sb.append(offset).append("</qos>\n");

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
                   "   <rcvTimestamp millis='1007764305862'>\n" +
                   "      2001-12-07 23:31:45.862\n" +
                   "   </rcvTimestamp>\n" +
                   "   <queue index='0' size='1'>\n" +
                   "   </queue>\n" +
                   "</qos>";

      UpdateQoS up = new UpdateQoS(xml);
      Log.info("Test", "#1\n" + up.toXml());

      up = new UpdateQoS(up.toXml());
      Log.exit("Test", "#2\n" + up.toXml());
   }
}
