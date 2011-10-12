/*------------------------------------------------------------------------------
Name:      UpdateQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import java.util.Map;
import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;

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
 *     &lt;subscribe id='__subId:1/>
 *     &lt;rcvTimestamp nanos='1007764305862000002'> &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
 *           2001-12-07 23:31:45.862000002   &lt;!-- The nanos from above but human readable -->
 *     &lt;/rcvTimestamp>
 *     &lt;expiration lifeTime='1200'/> &lt;!-- The overall life time of the message [milliseconds] -->
 *     &lt;queue index='0' of='1'/> &lt;!-- If queued messages are flushed on login -->
 *     &lt;redeliver>4&lt;/redeliver>
 *     &lt;route>
 *        &lt;node id='heron'/>
 *     &lt;/route>
 *     &lt;clientProperty name='transactionId' type='int'>120001&lt;/clientProperty>
 *     &lt;clientProperty name='transactionId' type='String' encoding='base64'>OKFKAL==&lt;/clientProperty>
 *  &lt;/qos>
 * </pre>
 * The receive timestamp can be delivered in human readable form as well
 * by setting on server command line:
 * <pre>
 *   -cb.receiveTimestampHumanReadable true
 *
 *   &lt;rcvTimestamp nanos='1015959656372000000'>
 *     2002-03-12 20:00:56.372
 *   &lt;/rcvTimestamp>
 * </pre>
 * @see org.xmlBlaster.util.qos.MsgQosData
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html">update interface</a>
 */
public final class UpdateQos
{
   private final Global glob;
   private final MsgQosData msgQosData;

   /**
    * Default constructor for transient messages.
    */
   public UpdateQos(Global glob, MsgQosData msgQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;

      this.msgQosData = msgQosData;
      this.msgQosData.setMethod(MethodName.UPDATE);
   }

   /**
    * Constructs the specialized quality of service object for a update() call.
    */
   public UpdateQos(Global glob, String xmlQos) throws XmlBlasterException {
      this(glob, glob.getMsgQosFactory().readObject(xmlQos));
   }
   
   /**
    * Convenience method to get the raw content as a string, the encoding is UTF-8 if not specified by clientProperty {@link Constants#CLIENTPROPERTY_CONTENT_CHARSET}
    * @return never null
    */
   public String getContentStr(byte[] msgContent) throws XmlBlasterException {
      if (getData() == null)
         return Constants.toUtf8String(msgContent);
      return getData().getContentStr(msgContent);
   }

   /**
    * Convenience method to get the raw content as a string, the encoding is UTF-8 if not specified by clientProperty {@link Constants#CLIENTPROPERTY_CONTENT_CHARSET}
    * @return never null
    */
   public String getContentStrNoEx(byte[] msgContent) {
      if (getData() == null)
         return Constants.toUtf8String(msgContent);
      return getData().getContentStrNoEx(msgContent);
   }

   /**
    * Get the QoS data object which i'm hiding
    */
   public MsgQosData getData() {
      return this.msgQosData;
   }

   public Global getGlobal() {
      return this.glob;
   }

   /**
    * Access sender name.
    * @return loginName and session of sender
    */
   public SessionName getSender() {
      return this.msgQosData.getSender();
   }

   /**
    * Message priority.
    * @return priority 0-9
    * @see org.xmlBlaster.util.def.Constants
    */
   public PriorityEnum getPriority() {
      return this.msgQosData.getPriority();
   }

   /**
    * Returns > 0 if the message probably is redelivered. 
    * @return == 0 The message is guaranteed to be delivered only once.
    */
   public int getRedeliver() {
      return this.msgQosData.getRedeliver();
   }

   /**
    * Access state of message.
    * @return OK (Other values are not yet supported)
    */
   public String getState() {
      return this.msgQosData.getState();
   }

   /**
    * True if the message is OK
    */
   public boolean isOk() {
      return this.msgQosData.isOk();
   }

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   public boolean isErased() {
      return this.msgQosData.isErased();
   }

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   public boolean isTimeout() {
      return this.msgQosData.isTimeout();
   }

   /**
    * True on cluster forward problems
    */
   public boolean isForwardError() {
      return this.msgQosData.isForwardError();
   }

   /**
    * Is this a volatile message?
    */
   public boolean isVolatile() {
      return this.msgQosData.isVolatile();
   }

   /**
    * Was this message subscribed?
    */
   public boolean isSubscribable() {
      return this.msgQosData.isSubscribable();
   }

   /**
    * Did i reveive the message in PtP mode?
    */
   public boolean isPtp() {
      return this.msgQosData.isPtp();
   }

   /**
    * Is this a persistent message?
    */
   public boolean isPersistent() {
      return this.msgQosData.isPersistent();
   }

   /**
    * Is this a readonly message?
    */
   public boolean isReadonly() {
      return this.msgQosData.isReadonly();
   }

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   public String getSubscriptionId() {
      return this.msgQosData.getSubscriptionId();
   }

   /**
    * If persistent messages where in queue, this is flushed on login. 
    * @return The number of queued messages
    */
   public long getQueueSize() {
      return this.msgQosData.getQueueSize();
   }

   /**
    * If persistent messages where in queue, this is flushed on login. 
    * @return The index of the message of the queue
    */
   public long getQueueIndex() {
      return this.msgQosData.getQueueIndex();
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message was created - arrived at xmlBlaster server.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   public Timestamp getRcvTimestamp() {
      return this.msgQosData.getRcvTimestamp();
   }

   /*
    * The local xmlBlaster node which delivered the message (does not need to be the master). 
    * @return The xmlBlaster cluster node which delivered the message
   public String getLocalNodeId() {
      return nodeId;
   }
    */

   /**
    * Human readable form of message receive time in xmlBlaster server,
    * in SQL representation e.g.:<br />
    * 2001-12-07 23:31:45.862000004
    */
   public String getRcvTime() {
      return this.msgQosData.getRcvTimestamp().toString();
   }

   /**
    * Approxiamte millis counted from now when message will be discarded
    * by xmlBlaster.
    * Calculated by xmlBlaster just before sending the update, so there
    * will be an offset (the time sending the message to us).
    * @return The time to live for this message or -1 (unlimited) if not known
    */
   public long getRemainingLifeStatic() {
      return this.msgQosData.getRemainingLifeStatic();
   }

   /**
    * @return never null, but may have length==0
    */
   public RouteInfo[] getRouteNodes() {
      return this.msgQosData.getRouteNodes();
   }

   /**
    * Access all client properties. 
    * @return a map The return is unordered and the map values are of type ClientProperty. 
    * @see org.xmlBlaster.util.qos.ClientProperty
    */
   public final Map getClientProperties() {
      return this.msgQosData.getClientProperties();
   }

   /**
    * Read back a property. 
    * @return The client property or null if not found
    */
   public ClientProperty getClientProperty(String key) {
      return this.msgQosData.getClientProperty(key);
   }

   /**
    * Access the String client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final String getClientProperty(String name, String defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Access the integer client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final int getClientProperty(String name, int defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Access the boolean client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final boolean getClientProperty(String name, boolean defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Access the double client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final double getClientProperty(String name, double defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Access the float client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final float getClientProperty(String name, float defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Access the byte client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final byte getClientProperty(String name, byte defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Access the byte[] client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final byte[] getClientProperty(String name, byte[] defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Access the long client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final long getClientProperty(String name, long defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Access the short client property. 
    * @param name The property key
    * @param defaultValue The value to return if the property is not known
    */
   public final short getClientProperty(String name, short defaultValue) {
      return this.msgQosData.getClientProperty(name, defaultValue);
   }
   
   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public String toXml() {
      return toXml((String)null, (Properties)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return this.msgQosData.toXml(extraOffset);
   }

   /**
    * Overwrite qosData.toXml
    * @param extraOffset
    * @param forceReadable
    * @return
    */
   public String toXml(String extraOffset, Properties props) {
      return this.msgQosData.toXml(extraOffset, props);
   }

   public String toString() {
      return toXml(null);
   }
}
