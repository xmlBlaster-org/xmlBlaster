/*------------------------------------------------------------------------------
Name:      UpdateQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: UpdateQoS.java,v 1.22 2002/04/26 21:31:46 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.RcvTimestamp;
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
 *     &lt;state>OK&lt;/state>
 *     &lt;sender>Tim&lt;/sender>
 *     &lt;subscriptionId>subscriptionId:__sys__TotalMem&lt;/subscriptionId>
 *     &lt;rcvTimestamp nanos='1007764305862000002'> &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
 *           2001-12-07 23:31:45.862000002   &lt;!-- The nanos from above but human readable -->
 *     &lt;/rcvTimestamp>
 *     &lt;expiration remainingLife='1200'/> &lt;!-- Calculated relative to when xmlBlaster has sent the message [milliseconds] -->
 *     &lt;queue index='0' of='1'/> &lt;!-- If queued messages are flushed on login -->
 *     &lt;redeliver>4&lt;/redeliver>
 *     &lt;route>
 *        &lt;node id='heron'/>
 *     &lt;/route>
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

   private boolean inExpiration = false;
   /** To calculate the remainingLife, -1 if not known */
   private long expirationTimestamp = -1L;

    /** helper flag for SAX parsing: parsing inside <subscriptionId> ? */
   private boolean inSubscriptionId = false;
   /** If Pub/Sub style update: contains the subscribe ID which caused this update */
   private String subscriptionId = null;

   /** helper flag for SAX parsing: parsing inside <rcvTimestamp> ? */
   private boolean inRcvTimestamp = false;
   private Timestamp rcvTimestamp;

   private boolean inNode = false;
   private boolean inRoute = false;
   /** The xmlBlaster cluster node which delivered the message */
   private String nodeId = null; 

   private int queueIndex = -1;
   private int queueSize = -1;
   private boolean inQueue = false;

   /** helper flag for SAX parsing: parsing inside <redeliver> ? */
   private boolean inRedeliver = false;
   /** the number of resend tries on failure */
   private int redeliver = 0;


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
    * Returns > 0 if the message probably is redelivered. 
    * @return == 0 The message is guaranteed to be delivered only once.
    */
   public int getRedeliver()
   {
      return redeliver;
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
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   public final Timestamp getRcvTimestamp()
   {
      return rcvTimestamp;
   }

   /**
    * The local xmlBlaster node which deliverd the node (does not need to be the master). 
    * @return The xmlBlaster cluster node which delivered the message
    */
   public String getLocalNodeId() {
      return nodeId;
   }

   /**
    * Human readable form of message receive time in xmlBlaster server,
    * in SQL representation e.g.:<br />
    * 2001-12-07 23:31:45.862000004
    */
   public final String getRcvTime()
   {
      return rcvTimestamp.toString();
   }

    /**
     * Approxiamte millis counted from now when message will be discarded
     * by xmlBlaster.
     * Calculated by xmlBlaster just before sending the update, so there
     * will be an offset (the time sending the message to us).
     * @return The time to live for this message or -1 if not known
     */
   public final long getRemainingLife()
   {
      if (expirationTimestamp == -1L)  return -1L;
      return expirationTimestamp - System.currentTimeMillis();
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

      if (name.equalsIgnoreCase("redeliver")) {
         if (!inQos)
            return;
         inRedeliver = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <redeliver> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
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
               if( attrs.getQName(i).equalsIgnoreCase("nanos") ) {
                 String tmp = attrs.getValue(i).trim();
                 try { rcvTimestamp = new RcvTimestamp(Long.parseLong(tmp)); } catch(NumberFormatException e) { Log.error(ME, "Invalid rcvTimestamp - nanos =" + tmp); };
               }
            }
            // if (Log.TRACE) Log.trace(ME, "Found rcvTimestamp tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("expiration")) {
         if (!inQos)
            return;
         inExpiration = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("remainingLife") ) {
                 String tmp = attrs.getValue(i).trim();
                 try {
                    long remainingLife = Long.parseLong(tmp);
                    this.expirationTimestamp = System.currentTimeMillis() + remainingLife;
                 } catch(NumberFormatException e) { Log.error(ME, "Invalid remainingLife - millis =" + tmp); };
               }
            }
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

      if (name.equalsIgnoreCase("route")) {
         if (!inQos)
            return;
         inRoute = true;
         return;
      }

      if (name.equalsIgnoreCase("node")) {
         if (!inQos) return;
         if (!inRoute) return;
         inNode = true;
         String tmp = attrs.getValue("id");
         if (tmp != null) nodeId = tmp.trim();
         tmp = attrs.getValue("stratum");
         if (tmp != null) { try { /* Integer.parseInt(tmp.trim()); */ } catch(NumberFormatException e) { Log.error(ME, "Invalid <route><node stratum='" + tmp + "'"); }; }
         tmp = attrs.getValue("timestamp");
         if (tmp != null) { try { /* Long.parseLong(tmp.trim()); */ } catch(NumberFormatException e) { Log.error(ME, "Invalid <route><node timestamp='" + tmp + "'"); }; }
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

      if(name.equalsIgnoreCase("redeliver")) {
         inRedeliver = false;
         String tmp = character.toString().trim();
         try { queueIndex = Integer.parseInt(tmp); } catch(NumberFormatException e) { Log.error(ME, "Invalid queue - redeliver =" + tmp); };
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
         // rcvTime = character.toString().trim();
         // if (Log.TRACE) Log.trace(ME, "Found message rcvTimestamp = " + rcvTimestamp);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("expiration")) {
         inExpiration = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("queue")) {
         inQueue = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("route")) {
         inRoute = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("node")) {
         inNode = false;
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
         sb.append(offset).append("   <state>").append(state).append("</state>");
      }
      if (sender != null) {
         sb.append(offset).append("   <sender>").append(sender).append("</sender>");
      }
      if (subscriptionId != null) {
         sb.append(offset).append("   <subscriptionId>").append(subscriptionId).append("</subscriptionId>");
      }

      if (getRcvTimestamp() != null)
         sb.append(getRcvTimestamp().toXml(extraOffset+"   ", true));
      else {
         Log.error(ME, "Missing rcvTimestamp in update QoS");
         sb.append(offset).append("   <error>No rcvTimestamp</error>");
      }
      sb.append(offset).append("   <expiration remainingLife='").append(getRemainingLife()).append("'/>");

      if(getQueueSize() > 0) {
         sb.append(offset).append("   <queue index='"+getQueueIndex()+"' size='"+getQueueSize()+"'/>");
      }

      if(getRedeliver() > 0) {
         sb.append(offset).append("   <redeliver>").append(getRedeliver()).append("</redeliver>");
      }

      sb.append(offset).append("</qos>\n");

      return sb.toString();
   }

   /**
    * Wrapper to create the returned QoS (with update method). 
    * Used by the xmlBlaster server. 
    * Creates the callback QoS of the update() method. 
    * <p />
    * It is usually a subset of this xml string:
    * <pre>
    *   &lt;qos>
    *    &lt;state>ERROR&lt;/state>
    *    &lt;sender>joe&lt;/sender>
    *    &lt;subscriptionId>subscriptionId:12003&lt;/subscriptionId>
    *    &lt;rcvTimestamp nanos='1015959656372000000'/>     
    *    &lt;expiration remainingLife='20000'/>
    *    &lt;queue index='3' size='12'/>
    *    &lt;redeliver>4&lt;/redeliver>
    *   &lt;/qos>
    *
    * The receive timestamp can be delivered in human readable form as well
    * by setting on server command line
    *
    *   -cb.recieveTimestampHumanReadable true
    *
    *   &lt;rcvTimestamp nanos='1015959656372000000'>
    *     2002-03-12 20:00:56.372
    *   &lt;/rcvTimestamp>
    * </pre>
    * @param index Index of entry in queue
    * @param max Number of entries in queue
    * @param state One of Constants.STATE_OK | Constants.STATE_EXPIRED | Constants.STATE_ERASED
    * @param redeliver If > 0, the message is redelivered after a failure
    */
   public static final String toXml(String subscriptionId,
                 org.xmlBlaster.engine.MessageUnitWrapper msgUnitWrapper,
                 int index, int max, String state, int redeliver, String nodeId) {
      if (msgUnitWrapper == null || state == null) {
         Log.error("UpdateQos", "Arguments for UpdateQos are invalid: subscriptionId == " + subscriptionId + " msgUnitWrapper=" + msgUnitWrapper + " state=" + state);
         throw new IllegalArgumentException("Arguments for UpdateQos are invalid");
      }

      // Check with RequestBroker.get() !!!
      StringBuffer buf = new StringBuffer(512);
      buf.append("\n<qos>");

      if (!org.xmlBlaster.engine.helper.Constants.STATE_OK.equals(state))
         buf.append("\n <state>").append(state).append("</state>");

      buf.append("\n <sender>").append(msgUnitWrapper.getPublisherName()).append("</sender>");

      if (subscriptionId != null)
         buf.append("\n <subscriptionId>").append(subscriptionId).append("</subscriptionId>");
      
      buf.append(msgUnitWrapper.getXmlRcvTimestamp());

      if (msgUnitWrapper.getPublishQos().getRemainingLife() > 0L)
         buf.append("\n <expiration remainingLife='").append(msgUnitWrapper.getPublishQos().getRemainingLife()).append("'/>");

      if (max > 0)
         buf.append("\n <queue index='").append(index).append("' size='").append(max).append("'/>");
      if (redeliver > 0)
         buf.append("\n <redeliver>").append(redeliver).append("</redeliver>");
      if (nodeId != null && nodeId.length() > 0) {
         buf.append("\n <route>"); // server internal added routing informations
         buf.append("\n  <node id='").append(nodeId).append("'/>");
         buf.append("\n </route>"); // server internal added routing informations
      }
      buf.append("\n</qos>");
      return buf.toString();
   }

   public final String toString()
   {
      return toXml(null);
   }


   /**
    *  For testing invoke: java org.xmlBlaster.client.UpdateQoS
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
                   "   <rcvTimestamp nanos='1007764305862000002'>\n" +
                   "      2001-12-07 23:31:45.862000002\n" +
                   "   </rcvTimestamp>\n" +
                   "   <expiration remainingLife='12000'/>\n" +
                   "   <queue index='0' size='1'/>\n" +
                   "   <redeliver>4</redeliver>\n" +
                   "   <route><node id='heron'/></route>\n" +
                   "</qos>";

      UpdateQoS up = new UpdateQoS(xml);
      Log.info("Test", "#1\n" + up.toXml());

      up = new UpdateQoS(up.toXml());
      Log.exit("Test", "#2\n" + up.toXml());
   }
}
