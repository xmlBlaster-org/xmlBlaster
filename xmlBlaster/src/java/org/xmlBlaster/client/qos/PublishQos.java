/*------------------------------------------------------------------------------
Name:      PublishQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.util.qos.TopicProperty;


/**
 * This class encapsulates the qos of a publish() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>publish</b> qos in Publish/Subcribe mode could look like this:<br />
 * <pre>
 *  &lt;qos>
 *     &lt;priority>5&lt;/priority>
 *     &lt;expiration lifeTime='60000' forceDestroy='false'/>
 *     &lt;isDurable />  &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
 *     &lt;forceUpdate>true&lt;/forceUpdate>
 *     &lt;readonly />
 *  &lt;/qos>
 * </pre>
 * A typical <b>publish</b> qos in PtP mode could look like this:<br />
 * <pre>
 *  &lt;qos>
 *     &lt;destination queryType='EXACT' forceQueuing='true'>
 *        joe
 *     &lt;/destination>
 *     &lt;destination>
 *        /node/heron/client/Tim/-2
 *     &lt;/destination>
 *  &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.publish.html">publish interface</a>
 */
public final class PublishQos
{
   private String ME = "PublishQos";
   private final Global glob;
   private final MsgQosData msgQosData;

   /**
    * Default constructor for transient messages.
    */
   public PublishQos(Global glob) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.msgQosData = new MsgQosData(glob, glob.getMsgQosFactory()); 
      setLifeTime(org.xmlBlaster.util.Global.instance().getProperty().get("message.lifeTime", -1L)); // TODO: use local glob
   }

   /**
    * Default constructor for transient PtP messages.
    * <p />
    * To make the message persistent, use the durable() method
    * @param destination The object containing the destination address.<br />
    *        To add more destinations, us the addDestination() method.
    */
   public PublishQos(Global glob, Destination destination) {
      this(glob);
      addDestination(destination);
   }

   /**
    * @param isDurable true = store the message persistently
    */
   public PublishQos(Global glob, boolean durable) {
      this(glob);
      setDurable(durable);
   }

   public MsgQosData getData() {
      return this.msgQosData;
   }

   /**
    * Message priority.
    * @return priority 0 (=Lowest) - 9 (=Highest)
    */
   public PriorityEnum getPriority() {
      return msgQosData.getPriority();
   }

   /**
    * Set message priority value, PriorityEnum.NORM_PRIORITY (5) is default. 
    * PriorityEnum.MIN_PRIORITY (0) is slowest
    * whereas PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.priority.html">The engine.qos.publish.priority requirement</a>
    */
   public void setPriority(PriorityEnum priority) {
      msgQosData.setPriority(priority);
   }

   /**
    * Send message to subscriber even if the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   public void setForceUpdate(boolean force) {
      msgQosData.setForceUpdate(force);
   }

   /**
    * Mark a message to be readonly.
    * <br />
    * Only the first publish() will be accepted, followers are denied.
    */
   public void setReadonly(boolean readonly) {
      msgQosData.setReadonly(readonly);
   }

   /**
    * Mark a message to be volatile or not.
    * <br />
    * A non-volatile messages stays in memory as long as the server runs<br />
    * A volatile messages exists only during publish and processing it (doing the updates).<br />
    * Defaults to false.
    * @deprecated Use setLifeTime(0L) and setForceDestroy(false) instead
    */
   public void setVolatile(boolean volatileFlag) {
      msgQosData.setVolatile(volatileFlag);
   }

   /**
    * @see #isVolatile()
    */
   public boolean isVolatile() {
      return msgQosData.isVolatile();
   }

   /**
    * Mark a message to be persistent.
    */
   public void setDurable(boolean durable) {
      msgQosData.setDurable(durable);
   }

   /**
    * The message expires after given milliseconds (message is erased).<p />
    * Clients will get a notify about expiration.<br />
    * This value is calculated relative to the rcvTimestamp in the xmlBlaster server.<br />
    * Passing -1 milliseconds asks the server for unlimited livespan, which
    * the server may or may not grant.
    * @param lifeTime in milliseconds
    */
   public void setLifeTime(long lifeTime) {
      msgQosData.setLifeTime(lifeTime);
   }

   /**
    * @param forceDestroy true Force message destroy on message expire<br />
    *        false On message expiry messages which are already in callback queues are delivered.
    */
   public void setForceDestroy(boolean forceDestroy) {
      msgQosData.setForceDestroy(forceDestroy);
   }

   /**
    * Add a destination where to send the message.
    * <p />
    * Note you can invoke this multiple times to send to multiple destinations.
    * @param destination  The loginName of a receiver or some destination XPath query
    */
   public void addDestination(Destination destination) {
      this.msgQosData.addDestination(destination);
   }

   /**
    * Access sender name.
    * @return loginName of sender or null if not known
    */
   public SessionName getSender() {
      return this.msgQosData.getSender();
   }

   /**
    * Access sender name.
    * @param loginName of sender
    */
   public void setSender(SessionName sender) {
      this.msgQosData.setSender(sender);
   }

   /**
    * @param state The state to return to the server.
    *   e.g. Contants.STATE_OK, see Constants.java
    */
   public void setState(String state) {
      this.msgQosData.setState(state);
   }

   public String getState() {
      return this.msgQosData.getState();
   }

   /**
    * @param stateInfo The state info attribute to return to the server.
    */
   public void setStateInfo(String stateInfo) {
      this.msgQosData.setStateInfo(stateInfo);
   }

   public String getStateInfo() {
      return this.msgQosData.getStateInfo();
   }

   /**
    * Administer/configure the message topic. 
    */
   public void setTopicProperty(TopicProperty topicProperty) {
      this.msgQosData.setTopicProperty(topicProperty);
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return toXml();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return msgQosData.toXml();
   }

   /**
    *  For testing invoke: java org.xmlBlaster.client.PublishQos
    */
   public static void main( String[] args ) throws XmlBlasterException
   {
      Global glob = new Global(args);
      {
         PublishQos qos =new PublishQos(new Global(args), new Destination(new SessionName(glob, "joe")));
         qos.addDestination(new Destination(new SessionName(glob, "Tim")));
         qos.setPriority(PriorityEnum.HIGH_PRIORITY);
         qos.setDurable(true);
         qos.setForceUpdate(true);
         qos.setReadonly(true);
         qos.setLifeTime(60000);
         System.out.println(qos.toXml());
      }
      {
         PublishQos qos =new PublishQos(null);
         System.out.println("Minimal '" + qos.toXml() + "'");
      }
   }
}
