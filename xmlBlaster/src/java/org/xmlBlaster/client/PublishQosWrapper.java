/*------------------------------------------------------------------------------
Name:      PublishQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: PublishQosWrapper.java,v 1.15 2001/12/16 21:25:33 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.util.XmlBlasterException;
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
 *     &lt;expiration timeToLive='60000'/>
 *     &lt;isDurable />  &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
 *     &lt;forceUpdate />
 *     &lt;readonly />
 *  &lt;/qos>
 * </pre>
 * A typical <b>publish</b> qos in PtP mode could look like this:<br />
 * <pre>
 *  &lt;qos>
 *     &lt;destination queryType='EXACT'>
 *        joe
 *     &lt;/destination>
 *     &lt;destination queryType='EXACT'>
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
   private boolean isVolatile = false;
   private boolean isDurable = false;
   private boolean forceUpdate = false;
   private boolean readonly = false;
   private long timeToLive = 0;



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
    * Mark a message to be updated even that the content didn't change.
    * <br />
    * Default is that xmlBlaster doesn't send messages to subscribed clients, if the message didn't change.
    */
   public void setForceUpdate()
   {
      this.forceUpdate = true;
   }


   /**
    * Mark a message to be readonly.
    * <br />
    * Only the first publish() will be accepted, followers are denied.
    */
   public void setReadonly()
   {
      this.readonly = true;
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
   public void setDurable()
   {
      this.isDurable = true;
   }

   /**
    * The message expires after given milliseconds (message is erased).<p />
    * Clients will get a notify about expiration.<br />
    * This value is calculated relative to the rcvTimestamp in the xmlBlaster server.<br />
    * Passing 0 milliseconds asks the server for unlimited livespan, which
    * the server may or may not grant.
    * @param timeToLive in milliseconds
    */
   public void setTimeToLive(long timeToLive)
   {
      this.timeToLive = timeToLive;
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
      if (timeToLive >= 0) {
         sb.append("\n   <expiration timeToLive='").append(timeToLive).append("'/>");
      }
      sb.append("\n   <isVolatile>").append(isVolatile).append("</isVolatile>");
      if (isDurable)
         sb.append("\n   <isDurable/>");
      if (forceUpdate)
         sb.append("\n   <forceUpdate/>");
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
      qos.setDurable();
      qos.setForceUpdate();
      qos.setReadonly();
      qos.setTimeToLive(60000);
      System.out.println(qos.toXml());
   }
}
