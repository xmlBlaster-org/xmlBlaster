/*------------------------------------------------------------------------------
Name:      UpdateQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: UpdateQos.java,v 1.8 2002/09/19 14:12:48 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.cluster.RouteInfo;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.helper.Constants;

import org.xml.sax.Attributes;

import java.util.Vector;

/**
 * QoS (quality of service) informations sent from server to client<br />
 * via the update() method from the I_Callback interface.
 * <p />
 * If you are a Java client you may use this class to parse the QoS argument.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos> &lt;!-- UpdateQos -->
 *     &lt;state id='OK'/>
 *     &lt;sender>Tim&lt;/sender>
 *     &lt;priority>5&lt;/priority>
 *     &lt;subscriptionId>subId:1&lt;/subscriptionId>
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
 * The receive timestamp can be delivered in human readable form as well
 * by setting on server command line:
 * <pre>
 *   -cb.recieveTimestampHumanReadable true
 *
 *   &lt;rcvTimestamp nanos='1015959656372000000'>
 *     2002-03-12 20:00:56.372
 *   &lt;/rcvTimestamp>
 * </pre>
 */
public class UpdateQos extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "UpdateQos";
   private final LogChannel log;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private boolean inState = false;
   /** the state of the message, defaults to "OK" if no state is returned */
   private String state = Constants.STATE_OK;

   /** helper flag for SAX parsing: parsing inside <priority> ? */
   private boolean inPriority = false;
   /** The priority of the message */
   private int priority = Constants.NORM_PRIORITY;

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
    * Vector containing RouteInfo objects
    */
   protected Vector routeNodeVec = null;
   /** Cache for RouteInfo in an array */
   protected RouteInfo[] routeNodes = null;


   /**
    * Constructs the specialized quality of service object for a update() call.
    */
   public UpdateQos(Global glob, String xmlQoS_literal) throws XmlBlasterException
   {
      this.log = glob.getLog("client");
      if (log.CALL) log.call(ME, "Entering UpdateQos() (a message arrived)");
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
    * Message priority.
    * @return priority 0-9
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public int getPriority()
   {
      return priority;
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
    * True if the message is OK
    */
   public final boolean isOk()
   {
      return Constants.STATE_OK.equals(state);
   }

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   public final boolean isErased()
   {
      return Constants.STATE_ERASED.equals(state);
   }

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   public final boolean isTimeout()
   {
      return Constants.STATE_TIMEOUT.equals(state);
   }

   /**
    * True on cluster forward problems
    */
   public final boolean isForwardError()
   {
      return Constants.STATE_FORWARD_ERROR.equals(state);
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
     * @return The time to live for this message or -1 (unlimited) if not known
     */
   public final long getRemainingLife()
   {
      if (expirationTimestamp == -1L)  return -1L;
      return expirationTimestamp - System.currentTimeMillis();
   }

   /**
    * @return never null, but may have length==0
    */
   public final RouteInfo[] getRouteNodes() {
      if (routeNodeVec == null)
         this.routeNodes = new RouteInfo[0];
      if (this.routeNodes == null)
         this.routeNodes = (RouteInfo[]) routeNodeVec.toArray(new RouteInfo[0]);
      return this.routeNodes;
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
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("id") ) {
                  state = attrs.getValue(i).trim();
               }/*
               else if( attrs.getQName(i).equalsIgnoreCase("userInfo") ) {
                  userInfo = attrs.getValue(i).trim();
               }  */
            }
            // if (log.TRACE) log.trace(ME, "Found state tag");
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
               log.warn(ME, "Ignoring sent <sender> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (log.TRACE) log.trace(ME, "Found sender tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("priority")) {
         if (!inQos)
            return;
         inPriority = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <priority> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (log.TRACE) log.trace(ME, "Found priority tag");
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
               log.warn(ME, "Ignoring sent <redeliver> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
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
               log.warn(ME, "Ignoring sent <subscriptionId> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (log.TRACE) log.trace(ME, "Found subscriptionId tag");
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
                 try { rcvTimestamp = new RcvTimestamp(Long.parseLong(tmp)); } catch(NumberFormatException e) { log.error(ME, "Invalid rcvTimestamp - nanos =" + tmp); };
               }
            }
            // if (log.TRACE) log.trace(ME, "Found rcvTimestamp tag");
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
                 } catch(NumberFormatException e) { log.error(ME, "Invalid remainingLife - millis =" + tmp); };
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
                 try { queueIndex = Integer.parseInt(tmp); } catch(NumberFormatException e) { log.error(ME, "Invalid queue - index =" + tmp); };
               }
               if( attrs.getQName(i).equalsIgnoreCase("size") ) {
                 String tmp = attrs.getValue(i).trim();
                 try { queueSize = Integer.parseInt(tmp); } catch(NumberFormatException e) { log.error(ME, "Invalid queue - index =" + tmp); };
               }
            }
            // if (log.TRACE) log.trace(ME, "Found queue tag");
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
         String id = null;
         if (tmp != null) id = tmp.trim();
         tmp = attrs.getValue("stratum");
         int stratum = 0;
         if (tmp != null) { try { stratum = Integer.parseInt(tmp.trim()); } catch(NumberFormatException e) { log.error(ME, "Invalid <route><node stratum='" + tmp + "'"); }; }
         tmp = attrs.getValue("timestamp");
         long timestamp = 0L;
         if (tmp != null) { try { timestamp = Long.parseLong(tmp.trim()); } catch(NumberFormatException e) { log.error(ME, "Invalid <route><node timestamp='" + tmp + "'"); }; }
         if (routeNodeVec == null) routeNodeVec = new Vector(5);
         routeNodeVec.addElement(new RouteInfo(new NodeId(id), stratum, new Timestamp(timestamp)));
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

      // if (log.TRACE) log.trace(ME, "Entering endElement for " + name);

      if(name.equalsIgnoreCase("state")) {
         inState = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("sender")) {
         inSender = false;
         sender = character.toString().trim();
         // if (log.TRACE) log.trace(ME, "Found message sender login name = " + sender);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("priority")) {
         inPriority = false;
         priority = Constants.getPriority(character.toString(), Constants.NORM_PRIORITY);
         // if (log.TRACE) log.trace(ME, "Found priority = " + priority);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("redeliver")) {
         inRedeliver = false;
         String tmp = character.toString().trim();
         try { queueIndex = Integer.parseInt(tmp); } catch(NumberFormatException e) { log.error(ME, "Invalid queue - redeliver =" + tmp); };
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("subscriptionId")) {
         inSubscriptionId = false;
         subscriptionId = character.toString().trim();
         // if (log.TRACE) log.trace(ME, "Found message subscriptionId = " + subscriptionId);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("rcvTimestamp")) {
         inRcvTimestamp = false;
         // rcvTime = character.toString().trim();
         // if (log.TRACE) log.trace(ME, "Found message rcvTimestamp = " + rcvTimestamp);
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

      sb.append(offset).append("<qos> <!-- UpdateQos -->");
      if (state != null) {
         sb.append(offset).append("   <state>").append(state).append("</state>");
      }
      if (sender != null) {
         sb.append(offset).append("   <sender>").append(sender).append("</sender>");
      }
      if (priority != Constants.NORM_PRIORITY) {
         sb.append(offset).append("   <priority>").append(priority).append("</priority>");
      }
      if (subscriptionId != null) {
         sb.append(offset).append("   <subscriptionId>").append(subscriptionId).append("</subscriptionId>");
      }

      if (getRcvTimestamp() != null)
         sb.append(getRcvTimestamp().toXml(extraOffset+"   ", true));
      else {
         log.error(ME, "Missing rcvTimestamp in update QoS");
         sb.append(offset).append("   <error>No rcvTimestamp</error>");
      }
      sb.append(offset).append("   <expiration remainingLife='").append(getRemainingLife()).append("'/>");

      if(getQueueSize() > 0) {
         sb.append(offset).append("   <queue index='"+getQueueIndex()+"' size='"+getQueueSize()+"'/>");
      }

      if(getRedeliver() > 0) {
         sb.append(offset).append("   <redeliver>").append(getRedeliver()).append("</redeliver>");
      }

      org.xmlBlaster.engine.cluster.RouteInfo[] routes = getRouteNodes();
      if (routes.length > 0) {
         sb.append(offset).append("<route>");
         for (int ii=0; ii<routes.length; ii++)
            sb.append(offset).append(routes[ii].toXml());
         sb.append(offset).append("</route>");
      }

      sb.append(offset).append("</qos>\n");

      return sb.toString();
   }

   /**
    * Wrapper to create the returned QoS (with update method). 
    * Used by the xmlBlaster server. 
    * Creates the callback QoS of the update() method. 
    * <p />
    * The XML syntax is described in the class description.
    * @param index Index of entry in queue
    * @param max Number of entries in queue
    * @param state One of Constants.STATE_OK | Constants.STATE_EXPIRED | Constants.STATE_ERASED
    * @param redeliver If > 0, the message is redelivered after a failure
    */
   public static final String toXml(String subscriptionId,
                 org.xmlBlaster.engine.MessageUnitWrapper msgUnitWrapper,
                 int index, int max, String state, int redeliver, String nodeId) {
      if (msgUnitWrapper == null || state == null) {
         Global.instance().getLog("client").error("UpdateQos", "Arguments for UpdateQos are invalid: subscriptionId == " + subscriptionId + " msgUnitWrapper=" + msgUnitWrapper + " state=" + state);
         throw new IllegalArgumentException("Arguments for UpdateQos are invalid");
      }

      // Check with RequestBroker.get() !!!
      StringBuffer sb = new StringBuffer(512);
      sb.append("\n<qos>");

      if (!org.xmlBlaster.engine.helper.Constants.STATE_OK.equals(state)) {
         sb.append("\n <state id='").append(state).append("'/>");
      }

      sb.append("\n <sender>").append(msgUnitWrapper.getPublisherName()).append("</sender>");
      sb.append("\n <priority>").append(msgUnitWrapper.getPublishQos().getPriority()).append("</priority>");

      if (subscriptionId != null)
         sb.append("\n <subscriptionId>").append(subscriptionId).append("</subscriptionId>");
      
      sb.append(msgUnitWrapper.getXmlRcvTimestamp());

      if (msgUnitWrapper.getPublishQos().getRemainingLife() > 0L)
         sb.append("\n <expiration remainingLife='").append(msgUnitWrapper.getPublishQos().getRemainingLife()).append("'/>");

      if (max > 0)
         sb.append("\n <queue index='").append(index).append("' size='").append(max).append("'/>");
      if (redeliver > 0)
         sb.append("\n <redeliver>").append(redeliver).append("</redeliver>");

      org.xmlBlaster.engine.cluster.RouteInfo[] routes = msgUnitWrapper.getPublishQos().getRouteNodes();
      if (routes.length > 0) {
         sb.append("\n <route>");
         for (int ii=0; ii<routes.length; ii++)
            sb.append(routes[ii].toXml());
         sb.append("\n </route>");
      }
      /*
      if (nodeId != null && nodeId.length() > 0) {
         sb.append("\n <route>"); // server internal added routing informations
         sb.append("\n  <node id='").append(nodeId).append("'/>");
         sb.append("\n </route>"); // server internal added routing informations
      }
      */
      sb.append("\n</qos>");
      return sb.toString();
   }

   public final String toString()
   {
      return toXml(null);
   }


   /**
    *  For testing invoke: java org.xmlBlaster.client.UpdateQos
    */
   public static void main( String[] args ) throws XmlBlasterException
   {
      Global glob = new Global(args);

      String xml = "<qos>\n" +
                   "   <state id='OK'/>\n" +
                   "   <sender>\n" +
                   "      Joe\n" +
                   "   </sender>\n" +
                   "   <priority>5</priority>\n" +
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

      UpdateQos up = new UpdateQos(glob, xml);
      System.out.println("Test: #1\n" + up.toXml());

      up = new UpdateQos(glob, up.toXml());
   }
}
