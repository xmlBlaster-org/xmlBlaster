/*------------------------------------------------------------------------------
Name:      PublishQoSWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: PublishQoSWrapper.java,v 1.1 1999/12/14 23:18:00 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import java.util.Vector;


/**
 * This class encapsulates the Message meta data and unique identifier (qos) of a publish() message.
 * <p />
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
public class PublishQoSWrapper extends QoSWrapper
{
   private String ME = "PublishQoSWrapper";
   private Vector destVec = null;
   private Vector xpathVec = null;
   private boolean isDurable = false;
   private boolean forceUpdate = false;
   private boolean readonly = false;
   private long expires = -99;
   private long erase = -99;



   /**
    * Constructor with given oid and mimeType.
    * @param oid is optional and will be generated if ""
    * @param mimeType the MIME type of the content e.g. "text/xml" or "image/gif"
    */
   public PublishQoSWrapper()
   {
   }


   /**
    * Add a destination where to send the message. 
    * <p />
    * Note you can invoke this multiple times to send to multiple destinations.
    * @param destination  The loginName of a receiver
    */
   public void addDestination(String destination)
   {
      if (destVec == null)
         destVec = new Vector();
      destVec.addElement(destination);
   }


   /**
    * Add a destination (XPath query syntax) where to send the message. 
    * <p />
    * Note you can invoke this multiple times to send to multiple destinations.
    * @param xpath The XPath query over the client meta data
    */
   public void addXPathDestination(String xpath)
   {
      if (xpathVec == null)
         xpathVec = new Vector();
      xpathVec.addElement(xpath);
   }


   /**
    * Converts the data in XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString()
   {
      StringBuffer sb = new StringBuffer();
      sb.append("<qos>\n");
      if (destVec != null) {
         for (int ii=0; ii<destVec.size(); ii++) {
            sb.append("   <destination queryType='EXACT'>\n");
            sb.append("      " + (String)destVec.elementAt(ii) + "\n");
            sb.append("   </destination>\n");
         }
      }
      if (xpathVec != null) {
         for (int ii=0; ii<xpathVec.size(); ii++) {
            sb.append("   <destination queryType='XPATH'>\n");
            sb.append("      " + (String)xpathVec.elementAt(ii) + "\n");
            sb.append("   </destination>\n");
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
