/*------------------------------------------------------------------------------
Name:      GetReturnQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.enum.MethodName;


/**
 * QoS (quality of service) informations sent from server to client<br />
 * via the return value of the get() method. 
 * <p />
 * If you are a Java client you may use this class to parse the QoS argument.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos> &lt;!-- GetReturnQos -->
 *     &lt;state id='OK'/>
 *     &lt;sender>Tim&lt;/sender>
 *     &lt;priority>5&lt;/priority>
 *     &lt;rcvTimestamp nanos='1007764305862000002'> &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
 *           2001-12-07 23:31:45.862000002   &lt;!-- The nanos from above but human readable -->
 *     &lt;/rcvTimestamp>
 *     &lt;expiration lifeTime='1200'/> &lt;!-- The overall life time of the message [milliseconds] -->
 *     &lt;route>
 *        &lt;node id='heron'/>
 *     &lt;/route>
 *  &lt;/qos>
 * </pre>
 * @author xmlBlaster@marcelruff.info
 */
public final class GetReturnQos
{
   private String ME = "GetReturnQos";
   private final Global glob;
   private final LogChannel log;
   private final MsgQosData msgQosData;

   /**
    * Default constructor for transient messages.
    */
   public GetReturnQos(Global glob, MsgQosData msgQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.log = glob.getLog("client");
      this.msgQosData = msgQosData;
      this.msgQosData.setMethod(MethodName.GET);
   }

   /**
    * Constructs the specialized quality of service object for a get() call.
    */
   public GetReturnQos(Global glob, String xmlQos) throws XmlBlasterException {
      this(glob, glob.getMsgQosFactory().readObject(xmlQos));
   }

   /**
    * Get the QoS data object which i'm hiding
    */
   public MsgQosData getData() {
      return this.msgQosData;
   }

   /**
    * Access sender name.
    * @return loginName of sender
    */
   public SessionName getSender() {
      return this.msgQosData.getSender();
   }

   /**
    * Message priority.
    * @return priority 0-9
    */
   public PriorityEnum getPriority() {
      return this.msgQosData.getPriority();
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
    * Is this a volatile message?
    */
   public boolean isVolatile() {
      return this.msgQosData.isVolatile();
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
    * The approximate receive timestamp (UTC time),
    * when message was created - arrived at xmlBlaster server.<br />
    * In nanoseconds elapsed since midnight, January 1, 1970 UTC
    */
   public Timestamp getRcvTimestamp() {
      return this.msgQosData.getRcvTimestamp();
   }

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
    * Calculated by xmlBlaster just before sending the get, so there
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
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public String toXml() {
      return toXml((String)null);
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

   public String toString() {
      return toXml(null);
   }
}
