/*------------------------------------------------------------------------------
Name:      EraseQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: EraseQosWrapper.java,v 1.2 2001/12/23 19:49:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Vector;


/**
 * This class encapsulates the Message meta data and unique identifier (qos) of a erase() message.
 * <p />
 * A full specified <b>erase</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;notify>false&lt;/notify>     &lt;!-- The subscribers shall not be notified when this message is destroyed -->
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class EraseQosWrapper extends QosWrapper
{
   private String ME = "EraseQosWrapper";

   /** not yet supported */
   private boolean notify = true;



   /**
    * Constructor for default qos (quality of service).
    */
   public EraseQosWrapper()
   {
   }


   /**
    * Constructor to receive notifies only (no data content will be delivered).
    * <p />
    * This may be useful if you have huge contents, and you only want to be informed about a change
    * @param notify true - notify subscribers that message is erased
    */
   public EraseQosWrapper(boolean notify)
   {
      this.notify = notify;
   }


   /**
    * @param notify true - notify subscribers that message is erased (default is true)
    */
   public void setNotify(boolean notify)
   {
      this.notify = notify;
   }

   /**
    * 
    */
   public boolean getNotify()
   {
      return this.notify;
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
      sb.append("   <notify>").append(getNotify()).append("</notify>\n");
      sb.append("</qos>");
      return sb.toString();
   }
}
