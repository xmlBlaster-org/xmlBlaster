/*------------------------------------------------------------------------------
Name:      PublishQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: PublishQosWrapper.java,v 1.7 2000/03/27 22:02:06 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Destination;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import java.util.Vector;


/**
 * This class encapsulates the qos of a publish() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>publish</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;destination queryType='EXACT'>
 *           Tim
 *        &lt;/destination>
 *        &lt;IsDurable />    &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class PublishQosWrapper extends QosWrapper
{
   private String ME = "PublishQosWrapper";
   private Vector destVec = null;
   private boolean isDurable = false;
   private boolean forceUpdate = false;
   private boolean readonly = false;
   private long expires = -99;
   private long erase = -99;



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
    * Mark a message to be persistent.
    */
   public void setDurable()
   {
      this.isDurable = true;
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
      sb.append("<qos>\n");
      if (destVec != null) {
         for (int ii=0; ii<destVec.size(); ii++) {
            Destination destination = (Destination)destVec.elementAt(ii);
            sb.append(destination.printOn("   ").toString()).append("\n");
         }
      }
      if (expires >= 0) {
         sb.append("   <expires>\n");
         sb.append("      " + expires + "\n");
         sb.append("   </expires>\n");
      }
      if (erase >= 0) {
         sb.append("   <erase>\n");
         sb.append("      " + erase + "\n");
         sb.append("   </erase>\n");
      }
      if (isDurable)
         sb.append("   <IsDurable />\n");
      if (forceUpdate)
         sb.append("   <ForceUpdate />\n");
      if (readonly)
         sb.append("   <Readonly />\n");
      sb.append("</qos>");
      return sb.toString();
   }
}
