/*------------------------------------------------------------------------------
Name:      SubscribeQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: SubscribeQosWrapper.java,v 1.7 2002/03/13 16:41:08 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Vector;


/**
 * This class encapsulates the Message meta data and unique identifier (qos) of a subscribe() message.
 * <p />
 * A full specified <b>subscribe</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;meta>false<&lt;/meta>      &lt;!-- Don't send me the xmlKey meta data on updates -->
 *        &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
 *        &lt;local>false&lt;/local>     &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class SubscribeQosWrapper extends QosWrapper
{
   private String ME = "SubscribeQosWrapper";

   /** not yet supported */
   private boolean meta = true;

   private boolean content = true;

   private boolean local = true;



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
    * @param content false - no data content is delivered
    */
   public SubscribeQosWrapper(boolean content)
   {
      this.content = content;
   }


   /**
    * Inhibit the delivery of messages to myself if i have published it.
    */
   public void setNoLocal()
   {
      this.local = false;
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
      if (!meta) sb.append("   <meta>false</meta>\n");
      if (!content) sb.append("   <content>false</content>\n");
      if (!local) sb.append("   <local>false</local>\n");
      sb.append("</qos>");
      return sb.toString();
   }
}
