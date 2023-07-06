/*------------------------------------------------------------------------------
Name:      PublishQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.def.MethodName;


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
 *     &lt;persistent />  &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
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
   private final Global glob;
   private final MsgQosData msgQosData;

   public PublishQos(Global glob, String serialData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.msgQosData = new MsgQosData(this.glob, this.glob.getMsgQosFactory(), serialData, MethodName.PUBLISH); 
      this.msgQosData.setMethod(MethodName.PUBLISH);
   }
   
   public PublishQos(Global glob, MsgQosData qosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.msgQosData = qosData;
   }

   /**
    * Default constructor for transient messages.
    */
   public PublishQos(Global glob) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.msgQosData = new MsgQosData(this.glob, this.glob.getMsgQosFactory(), MethodName.PUBLISH); 
      this.msgQosData.setMethod(MethodName.PUBLISH);
      /*
      // deprecated:
      long lt = this.glob.getProperty().get("message.lifeTime", -1L);
      if (lt != -1L) {
         this.msgQosData.getLifeTimeProp().setValue(lt, PropEntry.CREATED_BY_PROPFILE);
      }
      */
   }

   /**
    * Default constructor for transient PtP messages.
    * <p />
    * To make the message persistent, use the setPersistent() method
    * @param destination The object containing the destination address.<br />
    *        To add more destinations, us the addDestination() method.
    */
   public PublishQos(Global glob, Destination destination) {
      this(glob);
      addDestination(destination);
   }

   /**
    * @param persistent true = store the message persistently
    */
   public PublishQos(Global glob, boolean persistent) {
      this(glob);
      setPersistent(persistent);
   }
   
   /**
    * Sets the encoding in the client properties of the content of the message to be published.
    * Defaults to UTF-8
    * Is usually not interpreted by xmlBlaster. But special server plugins (like regex filter plugin may want to look into the content and should know the encoding
    * or the receiving client may want to know.
    * Note that "text/xml" contents are usually self contained,
    *  the processing instruction tells the xml encoding to the sax/dom parser and this clientProperty is ignored.
    * @param encoding like "cp1252"
    */
   public void setContentEncoding(String encoding) {
      if (encoding == null || encoding.trim().length() < 1) {
         if (getData() == null || getData().getClientProperties() == null)
            return;
         getData().getClientProperties().remove(Constants.CLIENTPROPERTY_CONTENT_CHARSET);
         return;
      }
      getData().addClientProperty(Constants.CLIENTPROPERTY_CONTENT_CHARSET, encoding.trim());
   }

   public MsgQosData getData() {
      return this.msgQosData;
   }

   /**
    * As a default setting you can subscribe on all messages (PtP or PubSub). 
    * @param isSubscribable true if Publish/Subscribe style is used<br />
    *         false Only possible for PtP messages to keep PtP secret (you can't subscribe them)
    */
   public void setSubscribable(boolean isSubscribable) {
      this.msgQosData.setSubscribable(isSubscribable);
   }

   /**
    * Message priority.
    * @return priority 0 (=Lowest) - 9 (=Highest)
    */
   public PriorityEnum getPriority() {
      return this.msgQosData.getPriority();
   }

   /**
    * Set message priority value, PriorityEnum.NORM_PRIORITY (5) is default. 
    * PriorityEnum.MIN_PRIORITY (0) is slowest
    * whereas PriorityEnum.MAX_PRIORITY (9) is highest priority.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.priority.html">The engine.qos.publish.priority requirement</a>
    */
   public void setPriority(PriorityEnum priority) {
      this.msgQosData.setPriority(priority);
   }

   /**
    * Send message to subscriber even if the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   public void setForceUpdate(boolean force) {
      this.msgQosData.setForceUpdate(force);
   }

   /**
    * Mark a message to be readonly.
    * <br />
    * Only the first publish() will be accepted, followers are denied.
    */
   public void setReadonly(boolean readonly) {
      this.msgQosData.setReadonly(readonly);
   }

   /**
    * Mark a message to be volatile or not.
    * <br />
    * A non-volatile messages stays in memory as long as the server runs or the message expires.<br />
    * A volatile messages exists only during publish and processing it (doing the updates).<br />
    * Defaults to false.
    * <br />
    * NOTE: This is a convenience method for setLifeTime(0L) and setForceDestroy(false).
    */
   public void setVolatile(boolean volatileFlag) {
      this.msgQosData.setVolatile(volatileFlag);
   }

   /**
    * @see #isVolatile()
    */
   public boolean isVolatile() {
      return this.msgQosData.isVolatile();
   }

   /**
    * Mark a message to be persistent.
    */
   public void setPersistent(boolean persistent) {
      this.msgQosData.setPersistent(persistent);
   }

   /**
    * The message expires after given milliseconds.
    * @param lifeTime in milliseconds
    * @see MsgQosData#setLifeTime(long)
    */
   public void setLifeTime(long lifeTime) {
      this.msgQosData.setLifeTime(lifeTime);
   }

   /**
    * Control message life cycle on message expiry, defaults to false. 
    * @param forceDestroy true Force message destroy on message expire<br />
    *        false On message expiry messages which are already in callback queues are delivered.
    */
   public void setForceDestroy(boolean forceDestroy) {
      this.msgQosData.setForceDestroy(forceDestroy);
   }

   /**
    * Add a destination where to send the message.
    * <p />
    * Note you can invoke this multiple times to send to multiple destinations.
    * <p />
    * Note that the default lifeTime is set to 0 (PtP are volatile messages as default)
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
   
   public void setAdministrative(boolean administrative) {
	   this.msgQosData.setAdministrative(administrative);
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
    * Sets a client property (an application specific property) to the
    * given value
    * @param key
    * @param value
    */
   public void addClientProperty(String key, Object value) {
      this.msgQosData.addClientProperty(key, value);
   }

   public void addClientProperty(String key, boolean value) {
      this.msgQosData.addClientProperty(key, value);
   }

   public void addClientProperty(String key, int value) {
      this.msgQosData.addClientProperty(key, value);
   }

   public void addClientProperty(String key, byte value) {
      this.msgQosData.addClientProperty(key, value);
   }

   public void addClientProperty(String key, long value) {
      this.msgQosData.addClientProperty(key, value);
   }

   public void addClientProperty(String key, short value) {
      this.msgQosData.addClientProperty(key, value);
   }

   public void addClientProperty(String key, double value) {
      this.msgQosData.addClientProperty(key, value);
   }

   public void addClientProperty(String key, float value) {
      this.msgQosData.addClientProperty(key, value);
   }

   /**
    * Read back a property. 
    * @return The client property or null if not found
    */
   public ClientProperty getClientProperty(String key) {
      return this.msgQosData.getClientProperty(key);
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
      return this.msgQosData.toXml();
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
         qos.setPersistent(true);
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
