/*------------------------------------------------------------------------------
Name:      PublishQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: PublishQos.java,v 1.16 2002/09/19 14:23:27 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.client.UpdateQos;

import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.cluster.RouteInfo;
import org.xmlBlaster.util.XmlBlasterException;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.Vector;
import java.io.*;


/**
 * Handling of publish() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the publish() method<br />
 * They are needed to control xmlBlaster
 * <p />
 * Example for Pub/Sub style:<p />
 * <pre>
 *  &lt;qos> &lt;!-- PublishQos -->
 *     &lt;sender>Tim&lt;/sender>
 *     &lt;priority>5&lt;/priority>
 *     &lt;rcvTimestamp nanos='129595811'/>       <!-- Only for persistence layer -->
 *     &lt;expiration remainingLife='129595811'/> <!-- Only for persistence layer -->
 *     &lt;isVolatile>false&lt;/isVolatile>
 *     &lt;isDurable/>
 *     &lt;readonly/>
 *  &lt;/qos>
 * </pre>
 * Example for PtP addressing style:&lt;p />
 * <pre>
 *  &lt;qos>
 *     &lt;destination queryType='EXACT' forceQueuing='true'>
 *        Tim
 *     &lt;/destination>
 *     &lt;destination queryType='EXACT'>
 *        Ben
 *     &lt;/destination>
 *     &lt;destination queryType='XPATH'>   <!-- Not supported yet -->
 *        //[GROUP='Manager']
 *     &lt;/destination>
 *     &lt;destination queryType='XPATH'>   <!-- Not supported yet -->
 *        //ROLE/[@id='Developer']
 *     &lt;/destination>
 *     &lt;sender>
 *        Gesa
 *     &lt;/sender>
 *     &lt;priority>7&lt;/priority>
 *     &lt;route>
 *        &lt;node id='bilbo' stratum='2' timestamp='34460239640' dirtyRead='true'/>
 *     &lt;/route>
 *  &lt;/qos>
 * </pre>
 * Note that receiveTimestamp is in nanoseconds, whereas all other time values are milliseconds
 * @see org.xmlBlaster.test.classtest.PublishQosTest
 */
public class PublishQos extends org.xmlBlaster.util.XmlQoSBase implements Serializable
{
   private String ME = "PublishQos";
   private final Global glob;
   private final LogChannel log;

   /**
    * A message lease lasts forever if not otherwise specified. <p />
    * The default message life cycle can be modified in xmlBlaster.properties:<br />
    * <code>message.lease.maxRemainingLife=3600000 # One hour lease</code><br />
    * Every message can set the remainingLife value between 1 and maxRemainingLife, 
    * 0 sets the life cycle on forever.
    */ // TODO: Change to use glob instead of Global singleton! What about performance? Put variable into Global?
   private static final long maxRemainingLife = Global.instance().getProperty().get("message.maxRemainingLife", 0L);

   // helper flags for SAX parsing
   private boolean inDestination = false; // parsing inside <destination> ?
   private boolean inSender = false; // parsing inside <sender> ?
   private boolean inPriority = false; // parsing inside <priority> ?
   private boolean inExpiration = false; // parsing inside <expiration> ?
   private boolean inRcvTimestamp = false; // parsing inside <rcvTimestamp> ?
   private boolean inIsVolatile = false; // parsing inside <isVolatile> ?
   private boolean inIsDurable = false; // parsing inside <isDurable> ?
   private boolean inReadonly = false; // parsing inside <readonly> ?
   private boolean inRoute = false; // parsing inside <route> ?

   /** Internal use only, is this message sent from the persistence layer? */
   private boolean fromPersistenceStore = false;

   // flags for QoS state
   private boolean usesXPathQuery = false;
   
   public static boolean DEFAULT_isVolatile = false;
   private boolean isVolatile = DEFAULT_isVolatile;

   public static boolean DEFAULT_isDurable = false;
   private boolean isDurable = DEFAULT_isDurable;

   /**
    * Send message to subscriber even the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   public static boolean DEFAULT_forceUpdate = true;
   private boolean forceUpdate = DEFAULT_forceUpdate;

   public static boolean DEFAULT_readonly = false;
   private boolean readonly = DEFAULT_readonly;

   /** 
    * The receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   private Timestamp rcvTimestamp;
   private boolean rcvTimestampFound = false;

   /** 
    * A message expires after some time and will be discarded.
    * Clients will get a notify about expiration.
    * This value is the elapsed milliseconds since UTC 1970 ...
    */
   private long expirationTimestamp = Long.MAX_VALUE;

   /** the sender (publisher) of this message (unique loginName) */
   private String sender = null;

   /** The priority of the message */
   private int priority = Constants.NORM_PRIORITY;

   /**
    * Vector for loginQoS, holding all destination addresses (Destination objects)
    */
   protected Vector destinationVec = null;
   protected Destination destination = null;

   /**
    * Vector containing RouteInfo objects
    */
   protected Vector routeNodeVec = null;
   /** Cache for RouteInfo in an array */
   protected RouteInfo[] routeNodes = null;
   private RouteInfo routeInfo = null;

   public long size = 0L;


   /**
    * Constructs the specialized quality of service object for a publish() call.
    * @param the XML based ASCII string
    */
   public PublishQos(Global glob, String xmlQoS_literal) throws XmlBlasterException
   {
      // if (log.TRACE) log.trace(ME, "\n"+xmlQoS_literal);
      this.glob = glob;
      this.log = glob.getLog("core");
      touchRcvTimestamp();
      setRemainingLife(getMaxRemainingLife());
      parseQos(xmlQoS_literal);
      if (xmlQoS_literal != null)
         size = xmlQoS_literal.length();
   }


   /**
    * Constructs the specialized quality of service object for a publish() call.
    * For internal use only, this message is sent from the persistence layer
    * @param the XML based ASCII string
    * @param true
    */
   public PublishQos(Global glob, String xmlQoS_literal, boolean fromPersistenceStore) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("core");
      if (!fromPersistenceStore) {
         touchRcvTimestamp();
         setRemainingLife(getMaxRemainingLife());
      }
      this.fromPersistenceStore = fromPersistenceStore;
      parseQos(xmlQoS_literal);
      if (xmlQoS_literal != null)
         size = xmlQoS_literal.length();
      if (fromPersistenceStore && !rcvTimestampFound) {
         log.error(ME, "Message from persistent store is missing rcvTimestamp");
      }
   }


   /**
    * Constructs the specialized quality of service object for a cluster update() forward call.
    */
   public PublishQos(Global glob, UpdateQos qos) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("core");
      setSender(qos.getSender());
      // !!! setState(qos.getState());
      this.rcvTimestamp = qos.getRcvTimestamp();
      setPriority(qos.getPriority());
      setRemainingLife(qos.getRemainingLife());
      RouteInfo[] routes = qos.getRouteNodes();
      for (int ii=0; ii<routes.length; ii++)
         addRouteInfo(routes[ii]);
      size = 0;
   }


   /**
    */
   private final void parseQos(String xmlQoS_literal) throws XmlBlasterException
   {
      if (!isEmpty(xmlQoS_literal)) // if possible avoid expensive SAX parsing
         init(xmlQoS_literal);  // use SAX parser to parse it (is slow)
      else xmlLiteral = "<qos></qos>";

      if (xmlQoS_literal != null)
         size = xmlQoS_literal.length();
   }


   /**
    * Test if Publish/Subscribe style is used.
    *
    * @return true if Publish/Subscribe style is used
    *         false if addressing of the destination is used
    */
   public final boolean isPubSubStyle()
   {
      return destinationVec == null;
   }


   /**
    * Test if Point to Point addressing style is used.
    *
    * @return true if addressing of the destination is used
    *         false if Publish/Subscribe style is used
    */
   public final boolean isPTP_Style()
   {
      return !isPubSubStyle();
   }


   /**
    * Mark a message to be volatile or not.
    * <br />
    * A non-volatile messages stays in memory as long as the server runs<br />
    * A volatile messages exists only during publish and processing it (doing the updates).<br />
    * Defaults to false.
    * @return true/false
    */
   public final boolean usesXPathQuery()
   {
      return usesXPathQuery;
   }


   /**
    * @return true/false
    */
   public final boolean isVolatile()
   {
      return isVolatile;
   }


   /**
    * @return true/false
    */
   public final boolean isDurable()
   {
      return isDurable;
   }


   /**
    * @return true/false
    */
   public final boolean forceUpdate()
   {
      return forceUpdate;
   }


   /**
    * @return true/false
    */
   public final boolean readonly()
   {
      return readonly;
   }


   /**
    * Access sender name.
    * @return loginName of sender or null if not known
    */
   public final String getSender()
   {
      return sender;
   }


   /**
    * Access sender name.
    * @return loginName of sender
    */
   public final void setSender(String sender)
   {
      this.sender = sender;
   }

   /**
    * Adds a new route hop to the QoS of this message. 
    * The added routeInfo is assumed to be one statum closer to the master
    * So we will rearrange the statum here. The given stratum in routeInfo
    * is used to recalculate the other nodes as well.
    */
   public final void addRouteInfo(RouteInfo routeInfo)
   {
      if (routeInfo == null) {
         log.error(ME, "Adding null routeInfo");
         return;
      }

      this.routeNodes = null; // clear cache

      if (routeNodeVec == null)
         routeNodeVec = new Vector();
      routeNodeVec.addElement(routeInfo);

      // Set stratum to new values
      int offset = routeInfo.getStratum();
      if (offset < 0) offset = 0;

      for (int ii=routeNodeVec.size()-1; ii>=0; ii--) {
         RouteInfo ri = (RouteInfo)routeNodeVec.elementAt(ii);
         ri.setStratum(offset++);
      }
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
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   public final int count(NodeId nodeId)
   {
      int count = 0;
      if (routeNodeVec == null)
         return count;
      for (int ii=0; ii<routeNodeVec.size(); ii++) {
         RouteInfo ri = (RouteInfo)routeNodeVec.elementAt(ii);
         if (ri.getNodeId().equals(nodeId))
            count++;
      }
      return count;
   }

   /**
    * Check if the message has already been at the given node (circulating message). 
    * @return How often the message has travelled the node already
    */
   public final boolean dirtyRead(NodeId nodeId)
   {
      int count = 0;
      if (routeNodeVec == null || nodeId == null)
         return false;
      for (int ii=0; ii<routeNodeVec.size(); ii++) {
         RouteInfo ri = (RouteInfo)routeNodeVec.elementAt(ii);
         if (ri.getNodeId().equals(nodeId)) {
            return ri.getDirtyRead();
         }
      }
      return false;
   }

   /**
    * Message priority.
    * @return priority 0-9
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public final int getPriority()
   {
      return priority;
   }


   /**
    * Set message priority value, Constants.NORM_PRIORITY (5) is default. 
    * Constants.MIN_PRIORITY (0) is slowest
    * whereas Constants.MAX_PRIORITY (9) is highest priority.
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public final void setPriority(int priority)
   {
      if (priority < Constants.MIN_PRIORITY || priority > Constants.MAX_PRIORITY)
         throw new IllegalArgumentException("Message priority must be in range 0-9");
      this.priority = priority;
   }


   /**
    * Internal use only, is this message sent from the persistence layer?
    * @return true/false
    */
   public final boolean isFromPersistenceStore()
   {
      return fromPersistenceStore;
   }

   /**
    * Internal use only, set if this message sent from the persistence layer
    * @param true/false
    */
   public final void setFromPersistenceStore(boolean fromPersistenceStore)
   {
      this.fromPersistenceStore = fromPersistenceStore;
   }

   /**
    * @return Milliseconds until message expiration (from now) or -1L if forever
    */
   public final long getRemainingLife()
   {
      if (expirationTimestamp < Long.MAX_VALUE)
         return expirationTimestamp - System.currentTimeMillis();
      else
         return -1L;
   }

   /**
    * @param remainingLife in milliseconds
    */
   public final void setRemainingLife(long remainingLife)
   {
      if (remainingLife < 0L && getMaxRemainingLife() < 0L)
         this.expirationTimestamp = Long.MAX_VALUE;
      else if (remainingLife >= 0L && getMaxRemainingLife() < 0L)
         this.expirationTimestamp = getRcvTimestamp().getMillis() + remainingLife;
      else if (remainingLife < 0L && getMaxRemainingLife() >= 0L)
         this.expirationTimestamp = getRcvTimestamp().getMillis() + getMaxRemainingLife();
      else if (remainingLife >= 0L && getMaxRemainingLife() >= 0L) {
         if (remainingLife <= getMaxRemainingLife())
            this.expirationTimestamp = getRcvTimestamp().getMillis() + remainingLife;
         else
            this.expirationTimestamp = getRcvTimestamp().getMillis() + getMaxRemainingLife();
      }
   }

   /**
    * The server default for max. span of life,
    * adjustable with property "message.maxRemainingLife"
    * @return max span of life for a message
    */
   public static final long getMaxRemainingLife()
   {
      return maxRemainingLife;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   public final Timestamp getRcvTimestamp()
   {
      if (rcvTimestamp == null) {
         log.error(ME, "rcvTimestamp is not set, setting it to current");
         touchRcvTimestamp();
      }
      return rcvTimestamp;
   }

   /**
    * Set timestamp to current time.
    */
   public final void touchRcvTimestamp()
   {
      rcvTimestamp = new RcvTimestamp();
   }

   /**
    * Get all the destinations of this message.
    * This should only be used with PTP style messaging<br />
    * Check <code>if (isPTP_Style()) ...</code> before calling this method
    *
    * @return a valid Vector containing 0 - n Strings with destination names (loginName of clients)<br />
    *         null if Publish/Subscribe style is used
    */
   public final Vector getDestinations()
   {
      return destinationVec;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (name.equalsIgnoreCase("destination")) {
         if (!inQos)
            return;
         inDestination = true;
         if (destinationVec == null) destinationVec = new Vector();
         destination = new Destination();
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("queryType") ) {
                  String queryType = attrs.getValue(i).trim();
                  if (queryType.equalsIgnoreCase("EXACT")) {
                     destination.setQueryType(queryType);
                  }
                  else if (queryType.equalsIgnoreCase("XPATH")) {
                     destination.setQueryType(queryType);
                  }
                  else
                     log.error(ME, "Sorry, destination queryType='" + queryType + "' is not supported");
               }
               else if( attrs.getQName(i).equalsIgnoreCase("forceQueuing") ) {
                  String tmp = attrs.getValue(i).trim();
                  if (tmp.length() > 0)
                     destination.forceQueuing(new Boolean(tmp).booleanValue());
               }
            }
         }
         String tmp = character.toString().trim(); // The address or XPath query string
         if (tmp.length() > 0) {
            destination.setDestination(tmp); // set address or XPath query string if it is before the ForceQueuing tag
            character.setLength(0);
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

      if (name.equalsIgnoreCase("expiration")) {
         if (!inQos)
            return;
         inExpiration = true;
         if (attrs != null) {
            int len = attrs.getLength();
            String tmp = attrs.getValue("remainingLife");
            if (tmp != null) {
               try { setRemainingLife(Long.parseLong(tmp.trim())); } catch(NumberFormatException e) { log.error(ME, "Invalid remainingLife - millis =" + tmp); };
            }
            else {
               log.warn(ME, "QoS <expiration> misses remainingLife attribute, setting default of " + getMaxRemainingLife());
               setRemainingLife(getMaxRemainingLife());
            }
            // if (log.TRACE) log.trace(ME, "Found expiration tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("rcvTimestamp")) {
         if (!inQos)
            return;
         if (attrs != null) {
            int len = attrs.getLength();
            if (fromPersistenceStore) {  // First we need the rcvTimestamp:
               String tmp = attrs.getValue("nanos");
               if (tmp != null) {
                  try { rcvTimestamp = new RcvTimestamp(Long.parseLong(tmp.trim())); rcvTimestampFound = true; } catch(NumberFormatException e) { log.error(ME, "Invalid rcvTimestamp - millis =" + tmp); };
               }
            }
         }
         inRcvTimestamp = true;
         return;
      }

      if (name.equalsIgnoreCase("route")) {
         if (!inQos)
            return;
         inRoute = true;
         return;
      }
      if (name.equalsIgnoreCase("node")) {
         if (!inRoute) {
            log.error(ME, "Ignoring <node>, it is not inside <route>");
            return;
         }

         if (attrs != null) {

            String id = attrs.getValue("id");
            if (id == null || id.length() < 1) {
               log.error(ME, "QoS <route><node> misses id attribute, ignoring node");
               return;
            }
            NodeId nodeId = new NodeId(id);

            int stratum = 0;
            String tmp = attrs.getValue("stratum");
            if (tmp != null) {
               try { stratum = Integer.parseInt(tmp.trim()); } catch(NumberFormatException e) { log.error(ME, "Invalid stratum =" + tmp); };
            }
            else {
               log.warn(ME, "QoS <route><node> misses stratum attribute, setting to 0: " + xmlLiteral);
               Thread.currentThread().dumpStack();
            }

            Timestamp timestamp = null;
            tmp = attrs.getValue("timestamp");
            if (tmp != null) {
               try { timestamp = new Timestamp(Long.parseLong(tmp.trim())); } catch(NumberFormatException e) { log.error(ME, "Invalid route Timestamp - nanos =" + tmp); };
            }
            else {
               log.warn(ME, "QoS <route><node> misses receive timestamp attribute, setting to 0");
               timestamp = new Timestamp(0L);
            }

            String tmpDirty = attrs.getValue("dirtyRead");
            boolean dirtyRead = org.xmlBlaster.engine.cluster.NodeDomainInfo.DEFAULT_dirtyRead;
            if (tmpDirty != null) {
               try { dirtyRead = new Boolean(tmpDirty.trim()).booleanValue(); } catch(NumberFormatException e) { log.error(ME, "Invalid dirtyRead =" + tmpDirty); };
            }

            if (log.TRACE) log.trace(ME, "Found node tag");

            routeInfo = new RouteInfo(nodeId, stratum, timestamp);
            if (tmpDirty != null)
               routeInfo.setDirtyRead(dirtyRead);
         }
         return;
      }

      // deprecated
      if (name.equalsIgnoreCase("forceQueuing")) {
         if (!inDestination)
            return;
         destination.forceQueuing(true);
         log.warn(ME, "forceQueuing is an attribute of destination - change your code");
         return;
      }

      if (name.equalsIgnoreCase("isVolatile")) {
         if (!inQos)
            return;
         inIsVolatile = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <isVolatile> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (log.TRACE) log.trace(ME, "Found isVolatile tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("isDurable")) {
         if (!inQos)
            return;
         isDurable = true;
         return;
      }

      if (name.equalsIgnoreCase("forceUpdate")) {
         if (!inQos)
            return;
         forceUpdate = true;
         return;
      }

      if (name.equalsIgnoreCase("readonly")) {
         if (!inQos)
            return;
         readonly = true;
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
      if (super.endElementBase(uri, localName, name) == true)
         return;

      if( name.equalsIgnoreCase("destination") ) {
         inDestination = false;
         String tmp = character.toString().trim(); // The address or XPath query string
         if (tmp.length() > 0) {
            destination.setDestination(tmp); // set address or XPath query string if it is before the forceQueuing tag
            character.setLength(0);
         }
         destinationVec.addElement(destination);
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

      if(name.equalsIgnoreCase("expiration")) {
         inRoute = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("expiration")) {
         inExpiration = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("rcvTimestamp")) {
         inRcvTimestamp = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("forceUpdate")) {
         inIsVolatile = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            forceUpdate = new Boolean(tmp).booleanValue();
         // if (log.TRACE) log.trace(ME, "Found forceUpdate = " + forceUpdate);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("isVolatile")) {
         inIsVolatile = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            isVolatile = new Boolean(tmp).booleanValue();
         // if (log.TRACE) log.trace(ME, "Found isVolatile = " + isVolatile);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("isDurable")) {
         inIsDurable = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            isDurable = new Boolean(tmp).booleanValue();
         // if (log.TRACE) log.trace(ME, "Found isDurable = " + isDurable);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("readonly")) {
         inReadonly = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            readonly = new Boolean(tmp).booleanValue();
         // if (log.TRACE) log.trace(ME, "Found readonly = " + readonly);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("node")) {
         addRouteInfo(routeInfo);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("route")) {
         inRoute = false;
         character.setLength(0);
         return;
      }

      character.setLength(0); // reset data from unknown tags
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

      // WARNING: This dump must be valid, as it is used by the
      //          persistent store
      sb.append(offset + "<qos> <!-- " + ME + " -->");

      if (destinationVec == null) {
         sb.append(offset + "   <Pub_Sub_style />");
      }
      else {
         for (int ii=0; ii<destinationVec.size(); ii++) {
            Destination destination = (Destination)destinationVec.elementAt(ii);
            sb.append(destination.toXml(extraOffset + "   "));
         }
      }
      if (sender != null) {
         sb.append(offset).append("   <sender>").append(sender).append("</sender>");
      }

      if (Constants.NORM_PRIORITY != priority)
         sb.append(offset).append("   <priority>").append(priority).append("</priority>");

      sb.append(rcvTimestamp.toXml(extraOffset + "   ", false));
      if (getRemainingLife() > 0L)
         sb.append(offset).append("   <expiration remainingLife='").append(getRemainingLife()).append("'/>");

      if (DEFAULT_isVolatile != isVolatile)
         sb.append(offset).append("   <isVolatile>").append(isVolatile).append("</isVolatile>");

      if (isDurable())
         sb.append(offset).append("   <isDurable/>");

      if (DEFAULT_forceUpdate != forceUpdate())
         sb.append(offset).append("   <forceUpdate>").append(forceUpdate()).append("</forceUpdate>");

      if (readonly())
         sb.append(offset).append("   <readonly/>");

      if (routeNodeVec != null) {
         sb.append(offset).append("   <route>");
         for (int ii=0; ii<routeNodeVec.size(); ii++) {
            RouteInfo routeInfo = (RouteInfo)routeNodeVec.elementAt(ii);
            sb.append(routeInfo.toXml(extraOffset + "   "));
         }
         sb.append(offset).append("   </route>");
      }

      sb.append(offset).append("</qos>");
      return sb.toString();
   }
}
