/*------------------------------------------------------------------------------
Name:      PublishQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: PublishQosWrapper.java,v 1.16 2002/03/13 16:41:08 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.Destination;
import java.util.Vector;


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
 *     &lt;expiration remainingLife='60000'/>
 *     &lt;isDurable />  &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
 *     &lt;forceUpdate>true</forceUpdate>
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
 *        Tim
 *     &lt;/destination>
 *  &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class PublishQosWrapper extends QosWrapper
{
   private String ME = "PublishQosWrapper";
   private Vector destVec = null;
   /** The priority of the message */
   private int priority = Constants.NORM_PRIORITY;
   private boolean isVolatile = false;
   private boolean isDurable = false;
   private boolean forceUpdate = true;
   private boolean readonly = false;
   private long remainingLife = XmlBlasterProperty.get("message.remainingLife", 0L);



   /**
    * Default constructor for transient messages.
    */
   public PublishQosWrapper()
   {
   }


   /**
    * Default constructor for transient PtP messages.
    * <p />
    * To make the message persistent, use the
    * @param destination The object containing the destination address.<br />
    *        To add more destinations, us the addDestination() method.
    */
   public PublishQosWrapper(Destination destination)
   {
      addDestination(destination);
   }


   /**
    * @param isDurable Store the message persistently
    */
   public PublishQosWrapper(boolean isDurable)
   {
      this.isDurable = isDurable;
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
    * Set message priority value, Constants.NORM_PRIORITY (5) is default. 
    * Constants.MIN_PRIORITY (0) is slowest
    * whereas Constants.MAX_PRIORITY (9) is highest priority.
    * @see org.xmlBlaster.engine.helper.Constants
    */
   public void setPriority(int priority)
   {
      if (priority < Constants.MIN_PRIORITY || priority > Constants.MAX_PRIORITY)
         throw new IllegalArgumentException("Message priority must be in range 0-9");
      this.priority = priority;
   }


   /**
    * Send message to subscriber even the content is the same as the previous?
    * <br />
    * Default is that xmlBlaster does send messages to subscribed clients, even the content didn't change.
    */
   public void setForceUpdate(boolean force)
   {
      this.forceUpdate = force;
   }


   /**
    * Mark a message to be readonly.
    * <br />
    * Only the first publish() will be accepted, followers are denied.
    */
   public void setReadonly(boolean readonly)
   {
      this.readonly = readonly;
   }


   /**
    * Mark a message to be volatile or not.
    * <br />
    * A non-volatile messages stays in memory as long as the server runs<br />
    * A volatile messages exists only during publish and processing it (doing the updates).<br />
    * Defaults to false.
    */
   public void isVolatile(boolean isVolatile)
   {
      this.isVolatile = isVolatile;
   }

   /**
    * @see #setIsVolatile()
    */
   public boolean isVolatile()
   {
      return this.isVolatile;
   }


   /**
    * Mark a message to be persistent.
    */
   public void setDurable(boolean durable)
   {
      this.isDurable = durable;
   }

   /**
    * The message expires after given milliseconds (message is erased).<p />
    * Clients will get a notify about expiration.<br />
    * This value is calculated relative to the rcvTimestamp in the xmlBlaster server.<br />
    * Passing 0 milliseconds asks the server for unlimited livespan, which
    * the server may or may not grant.
    * @param remainingLife in milliseconds
    */
   public void setRemainingLife(long remainingLife)
   {
      this.remainingLife = remainingLife;
   }

   /**
    * Add a destination where to send the message.
    * <p />
    * Note you can invoke this multiple times to send to multiple destinations.
    * @param destination  The loginName of a receiver or some destination XPath query
    */
   public void addDestination(Destination destination)
   {
      if (destVec == null)
         destVec = new Vector();
      destVec.addElement(destination);
   }


   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString()
   {
      return toXml();
   }


   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml()
   {
      StringBuffer sb = new StringBuffer();
      sb.append("\n<qos>");
      if (destVec != null) {
         for (int ii=0; ii<destVec.size(); ii++) {
            Destination destination = (Destination)destVec.elementAt(ii);
            sb.append(destination.toXml());
         }
      }
      sb.append("\n   <priority>").append(priority).append("</priority>");
      if (remainingLife >= 0) {
         sb.append("\n   <expiration remainingLife='").append(remainingLife).append("'/>");
      }
      if (isVolatile)
      sb.append("\n   <isVolatile>").append(isVolatile).append("</isVolatile>");
      if (isDurable)
         sb.append("\n   <isDurable/>");
      if (forceUpdate == false)
         sb.append("\n   <forceUpdate>").append(forceUpdate).append("</forceUpdate>");
      if (readonly)
         sb.append("\n   <readonly/>");
      sb.append("\n</qos>");
      return sb.toString();
   }

   /**
    *  For testing invoke: java org.xmlBlaster.client.PublishQosWrapper
    */
   public static void main( String[] args ) throws XmlBlasterException
   {
      PublishQosWrapper qos =new PublishQosWrapper(new Destination("joe"));
      qos.addDestination(new Destination("Tim"));
      qos.setPriority(Constants.HIGH_PRIORITY);
      qos.setDurable(true);
      qos.setForceUpdate(true);
      qos.setReadonly(true);
      qos.setRemainingLife(60000);
      System.out.println(qos.toXml());
   }
}
