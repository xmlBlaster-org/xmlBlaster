/*------------------------------------------------------------------------------
Name:      MsgQosSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.engine.cluster.NodeId;
import org.xmlBlaster.engine.cluster.RouteInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.qos.storage.TopicCacheProperty;

import java.io.*;
import java.util.ArrayList;

import org.xml.sax.*;
import org.xml.sax.helpers.*;


/**
 * Parsing xml QoS (quality of service) of publish() and update(). 
 * <p />
 * Example for Pub/Sub style:<p />
 * <pre>
 *  &lt;qos> &lt;
 *     &lt;state id='OK' info='Keep on running"/> <!-- Only for updates and PtP -->
 *     &lt;sender>Tim&lt;/sender>
 *     &lt;priority>5&lt;/priority>
 *     &lt;subscribe id='__subId:1'/> <!-- Only for updates, PtP message are marked with id='__subId:PtP' -->
 *     &lt;rcvTimestamp nanos='1007764305862000002'> &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
 *           2001-12-07 23:31:45.862000002   &lt;!-- The nanos from above but human readable -->
 *     &lt;/rcvTimestamp>
 *     &lt;expiration lifeTime='129595811' forceDestroy='false'/> <!-- Only for persistence layer -->
 *     &lt;queue index='0' of='1'/> &lt;!-- If queued messages are flushed on login -->
 *     &lt;isDurable/>
 *     &lt;redeliver>4&lt;/redeliver>             <!-- Only for updates -->
 *     &lt;route>
 *        &lt;node id='heron'/>
 *     &lt;/route>
 *     &lt;topic readonly='false' destroyDelay='60000' createDomEntry='true'>
 *        &lt;queue relating='topic' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000000' onOverflow='deadMessage'/>
 *        &lt;queue relating='history' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000000' onOverflow='exception'/>
 *     &lt;/topic>
 *  &lt;/qos>
 * </pre>
 * Example for PtP addressing style:&lt;p />
 * <pre>
 *  &lt;qos>
 *     &lt;subscribeable>false&lt;/subscribeable>  &lt;!-- false to make PtP message invisible for subscribes -->
 *     &lt;destination queryType='EXACT' forceQueuing='true'>
 *        Tim
 *     &lt;/destination>
 *     &lt;destination queryType='EXACT'>
 *        /node/heron/client/Ben/-2
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
 * <p>
 * Note that receiveTimestamp is in nanoseconds, whereas all other time values are milliseconds
 * </p>
 * The receive timestamp can be delivered in human readable form as well
 * by setting on server command line:
 * <pre>
 *   -cb.receiveTimestampHumanReadable true
 *
 *   &lt;rcvTimestamp nanos='1015959656372000000'>
 *     2002-03-12 20:00:56.372
 *   &lt;/rcvTimestamp>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.destination.PtP.html">The engine.qos.publish.destination.PtP requirement</a>
 * @author xmlBlaster@marcelruff.info
 */
public class MsgQosSaxFactory extends org.xmlBlaster.util.XmlQoSBase implements I_MsgQosFactory
{
   private String ME = "MsgQosSaxFactory";
   private final Global glob;
   private final LogChannel log;

   private  MsgQosData msgQosData;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private boolean inState = false;
   private boolean inSubscribe = false;
   private boolean inRedeliver = false;
   private boolean inTopic = false;
   private boolean inQueue = false;
   private boolean inMsgstore = false;
   private boolean inIsPubSub = false;
   private boolean inDestination = false;
   private boolean inSender = false;
   private boolean inPriority = false;
   private boolean inExpiration = false;
   private boolean inRcvTimestamp = false;
   private boolean inIsVolatile = false;
   private boolean inIsDurable = false;
   private boolean inReadonly = false;
   private boolean inRoute = false;

   private  Destination destination;
   private  RouteInfo routeInfo;

   private boolean sendRemainingLife = true;

   /**
    * Can be used as singleton. 
    */
   public MsgQosSaxFactory(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("core");
   }

   /**
    * Parses the given xml Qos and returns a MsgQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized MsgQosData readObject(String xmlQos) throws XmlBlasterException {
      if (xmlQos == null) {
         xmlQos = "<qos/>";
      }

      msgQosData = new MsgQosData(glob, this, xmlQos);

      if (!isEmpty(xmlQos)) // if possible avoid expensive SAX parsing
         init(xmlQos);      // use SAX parser to parse it (is slow)

      return msgQosData;
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
                  msgQosData.setState(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("info") ) {
                  msgQosData.setStateInfo(attrs.getValue(i).trim());
               }
            }
            // if (log.TRACE) log.trace(ME, "Found state tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("subscribeable")) {
         if (!inQos)
            return;
         inIsPubSub = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               log.warn(ME, "Ignoring sent <subscribeable> tag " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (log.TRACE) log.trace(ME, "Found subscribeable tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("destination")) {
         if (!inQos)
            return;
         inDestination = true;
         this.destination = new Destination();
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("queryType") ) {
                  String queryType = attrs.getValue(i).trim();
                  if (queryType.equalsIgnoreCase("EXACT")) {
                     this.destination.setQueryType(queryType);
                  }
                  else if (queryType.equalsIgnoreCase("XPATH")) {
                     this.destination.setQueryType(queryType);
                  }
                  else
                     log.error(ME, "Sorry, destination queryType='" + queryType + "' is not supported");
               }
               else if( attrs.getQName(i).equalsIgnoreCase("forceQueuing") ) {
                  String tmp = attrs.getValue(i).trim();
                  if (tmp.length() > 0) {
                     this.destination.forceQueuing(new Boolean(tmp).booleanValue());
                  }
               }
            }
         }
         String tmp = character.toString().trim(); // The address or XPath query string
         if (tmp.length() > 0) {
            this.destination.setDestination(new SessionName(glob, tmp)); // set address or XPath query string if it is before inner tags
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
            String tmp = attrs.getValue("lifeTime");
            if (tmp != null) {
               try { msgQosData.setLifeTime(Long.parseLong(tmp.trim())); } catch(NumberFormatException e) { log.error(ME, "Invalid lifeTime - millis =" + tmp); };
            }
            else {
               log.warn(ME, "QoS <expiration> misses lifeTime attribute, setting default of " + msgQosData.getMaxLifeTime());
               msgQosData.setLifeTime(msgQosData.getMaxLifeTime());
            }

            tmp = attrs.getValue("forceDestroy");
            if (tmp != null) {
               msgQosData.setForceDestroy(new Boolean(tmp.trim()).booleanValue());
            }
            
            tmp = attrs.getValue("remainingLife");
            if (tmp != null) {
               try { msgQosData.setRemainingLifeStatic(Long.parseLong(tmp.trim())); } catch(NumberFormatException e) { log.error(ME, "Invalid remainingLife - millis =" + tmp); };
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("topic")) {
         if (!inQos)
            return;
         inTopic = true;

         TopicProperty tmpProp = new TopicProperty(glob);
         
         String tmp = attrs.getValue("readonly");
         if (tmp != null) {
            tmpProp.setReadonly(new Boolean(tmp.trim()).booleanValue());
         }
         
         tmp = attrs.getValue("destroyDelay");
         if (tmp != null) {
            try { tmpProp.setDestroyDelay(Long.parseLong(tmp.trim())); } catch(NumberFormatException e) { log.warn(ME, "Invalid topic destroyDelay - millis =" + tmp); };
         }
         
         tmp = attrs.getValue("createDomEntry");
         if (tmp != null) {
            tmpProp.setCreateDomEntry(new Boolean(tmp.trim()).booleanValue());
         }
         
         msgQosData.setTopicProperty(tmpProp);

         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         if (!inQos)
            return;
         inQueue = true;
         if (attrs != null) {
            String indexVal = attrs.getValue("index");

            // UpdateQos contains queue informations
            if (indexVal != null) {
               try { msgQosData.setQueueIndex(Integer.parseInt(indexVal)); } catch(NumberFormatException e) { log.error(ME, "Invalid queue - index =" + indexVal); };
               String tmp = attrs.getValue("size");
               if (tmp != null) {
                  try { msgQosData.setQueueSize(Integer.parseInt(tmp)); } catch(NumberFormatException e) { log.error(ME, "Invalid queue - index =" + tmp); };
               }
               return;
            }

            if (inTopic) {
               String relatedVal = attrs.getValue("relating");
               if (relatedVal == null)
                  relatedVal = "topic";

               relatedVal = relatedVal.trim();
               if ("topic".equalsIgnoreCase(relatedVal)) {   // msgstore related='topic' is deprecated here! (is parsed now as msgstore, see below)
                  TopicCacheProperty tmpProp = new TopicCacheProperty(glob, glob.getId());
                  tmpProp.startElement(uri, localName, name, attrs);
                  msgQosData.getTopicProperty().setTopicCacheProperty(tmpProp);
               }
               else { // assuming related="history"
                  HistoryQueueProperty tmpProp = new HistoryQueueProperty(glob, glob.getId());
                  tmpProp.startElement(uri, localName, name, attrs);
                  msgQosData.getTopicProperty().setHistoryQueueProperty(tmpProp);
               }
               return;
            }

            log.warn(ME, "Found queue tag but don't know how to handle it: " + xmlLiteral);
         }
         return;
      }

      if (name.equalsIgnoreCase("msgstore")) {
         if (!inQos)
            return;
         inMsgstore = true;
         if (attrs != null) {
            if (inTopic) {
               String relatedVal = attrs.getValue("relating");
               //relatedVal = relatedVal.trim();  //   relatedVal = "topic";
               TopicCacheProperty tmpProp = new TopicCacheProperty(glob, glob.getId());
               tmpProp.startElement(uri, localName, name, attrs);
               msgQosData.getTopicProperty().setTopicCacheProperty(tmpProp);
               return;
            }

            log.warn(ME, "Found msgstore tag but don't know how to handle it: " + xmlLiteral);
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
                 try { msgQosData.setRcvTimestamp(new RcvTimestamp(Long.parseLong(tmp))); } catch(NumberFormatException e) { log.error(ME, "Invalid rcvTimestamp - nanos =" + tmp); };
               }
            }
         }
         inRcvTimestamp = true;
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
               //Thread.currentThread().dumpStack();
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
         log.error(ME, "forceQueuing is an attribute of destination - change your code");
         return;
      }

      if (name.equalsIgnoreCase("subscribe")) {
         if (!inQos)
            return;
         inSubscribe = true;
         if (attrs != null) {
            msgQosData.setSubscriptionId(attrs.getValue("id"));
         }
         return;
      }

      if (name.equalsIgnoreCase("isVolatile")) { // deprecated
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
         msgQosData.setDurable(true);
         return;
      }

      if (name.equalsIgnoreCase("forceUpdate")) {
         if (!inQos)
            return;
         msgQosData.setForceUpdate(true);
         return;
      }

      if (name.equalsIgnoreCase("readonly")) {
         if (!inQos)
            return;
         msgQosData.setReadonly(true);
         log.error(ME, "<qos><readonly/></qos> is deprecated, please use readonly as topic attribute <qos><topic readonly='true'></qos>");
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

      if(name.equalsIgnoreCase("state")) {
         inState = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("subscribeable")) {
         inIsPubSub = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0) {
            msgQosData.setIsSubscribeable(new Boolean(tmp).booleanValue());
         }
         character.setLength(0);
         return;
      }

      if( name.equalsIgnoreCase("destination") ) {
         inDestination = false;
         String tmp = character.toString().trim(); // The address or XPath query string
         if (tmp.length() > 0) {
            this.destination.setDestination(new SessionName(glob, tmp)); // set address or XPath query string if it is before the forceQueuing tag
            character.setLength(0);
         }
         msgQosData.addDestination(this.destination);
         return;
      }

      if(name.equalsIgnoreCase("sender")) {
         inSender = false;
         msgQosData.setSender(new SessionName(glob, character.toString().trim()));
         // if (log.TRACE) log.trace(ME, "Found message sender login name = " + msgQosData.getSender());
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("priority")) {
         inPriority = false;
         try {
            msgQosData.setPriority(PriorityEnum.parsePriority(character.toString()));
         }
         catch (IllegalArgumentException e) {
            msgQosData.setPriority(PriorityEnum.NORM_PRIORITY);
            log.warn(ME, "Problems parsing priority, setting priority to " + msgQosData.getPriority().toString() + ": " + e.toString());
         }
         // if (log.TRACE) log.trace(ME, "Found priority = " + msgQosData.getPriority());
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("expiration")) {
         inExpiration = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("topic")) {
         inTopic = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("queue")) {
         inQueue = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("msgstore")) {
         inMsgstore = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("rcvTimestamp")) {
         inRcvTimestamp = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("forceUpdate")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            msgQosData.setForceUpdate(new Boolean(tmp).booleanValue());
         // if (log.TRACE) log.trace(ME, "Found forceUpdate = " + msgQosData.getForceUpdate());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("subscribe")) {
         inSubscribe = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("isVolatile")) { // deprecated
         inIsVolatile = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0) {
            msgQosData.setVolatile(new Boolean(tmp).booleanValue());
            if (msgQosData.isVolatile()) {
               log.warn(ME, "Found 'isVolatile=" + msgQosData.isVolatile() + "' which is deprecated, use lifeTime==0&&forceDestroy==false instead");
            }
         }
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("isDurable")) {
         inIsDurable = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            msgQosData.setDurable(new Boolean(tmp).booleanValue());
         // if (log.TRACE) log.trace(ME, "Found isDurable = " + msgQosData.getIsDurable());
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("readonly")) {
         inReadonly = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            msgQosData.setReadonly(new Boolean(tmp).booleanValue());
         // if (log.TRACE) log.trace(ME, "Found readonly = " + msgQosData.readonly());
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("redeliver")) {
         inRedeliver = false;
         String tmp = character.toString().trim();
         try { msgQosData.setRedeliver(Integer.parseInt(tmp)); } catch(NumberFormatException e) { log.error(ME, "Invalid redeliver =" + tmp); };
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("node")) {
         msgQosData.addRouteInfo(routeInfo);
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
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(MsgQosData msgQosData, String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      // WARNING: This dump must be valid, as it is used by the
      //          persistent store
      sb.append(offset).append("<qos>");

      if (!msgQosData.isOk() || msgQosData.getStateInfo() != null && msgQosData.getStateInfo().length() > 0) {
         sb.append(offset).append(" <state id='").append(msgQosData.getState());
         if (msgQosData.getStateInfo() != null)
            sb.append("' info='").append(msgQosData.getStateInfo());
         sb.append("'/>");
      }

      if (msgQosData.isSubscribeableProp().isModified()) {
         if (msgQosData.isSubscribeable())
            sb.append(offset).append(" <subscribeable/>");
         else
            sb.append(offset).append(" <subscribeable>false</subscribeable>");
      }

      ArrayList list = msgQosData.getDestinations();
      if (list == null) {
         //sb.append(offset + " <Pub_Sub_style />");
      }
      else {
         for (int ii=0; ii<list.size(); ii++) {
            Destination destination = (Destination)list.get(ii);
            sb.append(destination.toXml(extraOffset+Constants.INDENT));
         }
      }
      if (msgQosData.getSender() != null) {
         sb.append(offset).append(" <sender>").append(msgQosData.getSender().getAbsoluteName()).append("</sender>");
      }

      if (PriorityEnum.NORM_PRIORITY != msgQosData.getPriority()) {
         if (PriorityEnum.NORM_PRIORITY.toString().equals(msgQosData.getPriority().toString())) {
            int hash1 = PriorityEnum.NORM_PRIORITY.hashCode();
            int hash2 = msgQosData.getPriority().hashCode();
            log.error(ME, "The strings should not equal: PriorityEnum.NORM_PRIORITY=" + PriorityEnum.NORM_PRIORITY + " hash1=" + hash1 +
                          " msgQosData.getPriority()=" + msgQosData.getPriority() + " hash2=" + hash2);
         }
         sb.append(offset).append(" <priority>").append(msgQosData.getPriority()).append("</priority>");
      }

      if (msgQosData.getSubscriptionId() != null)
         sb.append(offset).append(" <subscribe id='").append(msgQosData.getSubscriptionId()).append("'/>");

      if (msgQosData.getLifeTimeProp().isModified() || msgQosData.getForceDestroyProp().isModified()) {
         sb.append(offset).append(" <expiration");
         if (msgQosData.getLifeTimeProp().isModified())
            sb.append(" lifeTime='").append(msgQosData.getLifeTime()).append("'");
         if (sendRemainingLife()) {
            long remainCached = msgQosData.getRemainingLife();
            if (remainCached > 0)
               sb.append(" remainingLife='").append(remainCached).append("'");
            else if (msgQosData.getRemainingLifeStatic() >= 0)
               sb.append(" remainingLife='").append(msgQosData.getRemainingLifeStatic()).append("'");
         }
         if (msgQosData.getForceDestroyProp().isModified())
            sb.append(" forceDestroy='").append(msgQosData.isForceDestroy()).append("'");
         sb.append("/>");
      }

      if (msgQosData.getRcvTimestamp() != null)
         sb.append(msgQosData.getRcvTimestamp().toXml(extraOffset+Constants.INDENT, false));

      if(msgQosData.getQueueSize() > 0)
         sb.append(offset).append(" <queue index='").append(msgQosData.getQueueIndex()).append("' size='").append(msgQosData.getQueueSize()).append("'/>");

      //if (!msgQosData.isVolatileDefault())
      //   sb.append(offset).append(" <isVolatile>").append(msgQosData.isVolatile()).append("</isVolatile>");

      if (msgQosData.getDurableProp().isModified()) {
         if (msgQosData.isDurable())
            sb.append(offset).append(" <isDurable/>");
         else
            sb.append(offset).append(" <isDurable>false</isDurable>");
      }

      if (msgQosData.getForceUpdateProp().isModified()) {
         if (msgQosData.isForceUpdate())
           sb.append(offset).append(" <forceUpdate/>");
         else
           sb.append(offset).append(" <forceUpdate>false</forceUpdate>");
      }

      //if (msgQosData.isReadonly()) -> see topic attribute
      //   sb.append(offset).append(" <readonly/>");

      if(msgQosData.getRedeliver() > 0) {
         sb.append(offset).append(" <redeliver>").append(msgQosData.getRedeliver()).append("</redeliver>");
      }

      RouteInfo[] routeInfoArr = msgQosData.getRouteNodes();
      if (routeInfoArr.length > 0) {
         sb.append(offset).append(" <route>");
         for (int ii=0; ii<routeInfoArr.length; ii++) {
            sb.append(routeInfoArr[ii].toXml(extraOffset+Constants.INDENT));
         }
         sb.append(offset).append(" </route>");
      }

      if (msgQosData.hasTopicProperty()) {
         sb.append(msgQosData.getTopicProperty().toXml(extraOffset+Constants.INDENT));
      }

      sb.append(offset).append("</qos>");

      if (sb.length() < 16)
         return "<qos/>";  // minimal footprint

      return sb.toString();
   }

   /**
    * A human readable name of this factory
    * @return "MsgQosSaxFactory"
    */
   public String getName() {
      return "MsgQosSaxFactory";
   }

   /** Configure if remaingLife is sent in Qos (redesign approach to work with all QoS attributes */
   public void sendRemainingLife(boolean sendRemainingLife) { this.sendRemainingLife = sendRemainingLife; }
   public boolean sendRemainingLife() { return this.sendRemainingLife; }

}
