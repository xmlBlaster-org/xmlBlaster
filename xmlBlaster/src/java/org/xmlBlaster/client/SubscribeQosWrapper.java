/*------------------------------------------------------------------------------
Name:      SubscribeQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: SubscribeQosWrapper.java,v 1.5 2000/06/18 15:21:59 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Vector;


/**
 * This class encapsulates the Message meta data and unique identifier (qos) of a subscribe() message.
 * <p />
 * A full specified <b>subscribe</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;noMeta />       &lt;!-- Don't send me the xmlKey meta data on updates -->
 *        &lt;noContent />    &lt;!-- Don't send me the content data on updates (notify only) -->
 *        &lt;noLocal />      &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class SubscribeQosWrapper extends QosWrapper
{
   private String ME = "SubscribeQosWrapper";

   /** not yet supported */
   private boolean noMeta = false;

   private boolean noContent = false;

   private boolean noLocal = false;



   /**
    * Constructor for default qos (quality of service).
    */
   public SubscribeQosWrapper()
   {
   }


   /**
    * Constructor to receive notifies only (no data content will be delivered).
    * <p />
    * This may be useful if you have huge contents, and you only want to be informed about a change
    * @param noContent true - no data content is delivered
    */
   public SubscribeQosWrapper(boolean noContent)
   {
      this.noContent = noContent;
   }


   /**
    * Inhibit the delivery of messages to myself if i have published it.
    */
   public void setNoLocal()
   {
      this.noLocal = true;
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
      if (noMeta)
         sb.append("   <noMeta />\n");
      if (noContent)
         sb.append("   <noContent />\n");
      if (noLocal)
         sb.append("   <noLocal />\n");
      sb.append("</qos>");
      return sb.toString();
   }
}
