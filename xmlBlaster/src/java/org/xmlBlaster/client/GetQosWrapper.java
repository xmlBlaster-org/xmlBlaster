/*------------------------------------------------------------------------------
Name:      GetQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: GetQosWrapper.java,v 1.3 2002/03/13 16:41:08 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Vector;


/**
 * This class encapsulates the qos of a get() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>get()</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;meta>false<&lt;/meta>      &lt;!-- Don't send me the xmlKey meta data on updates -->
 *        &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class GetQosWrapper extends QosWrapper
{
   private String ME = "GetQosWrapper";
   private boolean meta = true;
   private boolean content = true;


   /**
    * Default constructor for transient messages.
    */
   public GetQosWrapper()
   {
   }


   /**
    * @param meta false: Store the message persistently
    */
   public GetQosWrapper(boolean meta)
   {
      this.meta = meta;
   }


   /**
    * Mark a message to be updated even that the content didn't change.
    * <br />
    * Default is that xmlBlaster doesn't send messages to subscribed clients, if the message didn't change.
    */
   public void setNoContent()
   {
      this.content = false;
   }


   /**
    * Mark a message to be persistent.
    */
   public void setNoMeta()
   {
      this.meta = false;
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
      sb.append("</qos>");
      return sb.toString();
   }
}
