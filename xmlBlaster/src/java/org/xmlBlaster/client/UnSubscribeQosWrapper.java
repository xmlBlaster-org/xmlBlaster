/*------------------------------------------------------------------------------
Name:      UnSubscribeQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: UnSubscribeQosWrapper.java,v 1.1 2001/12/23 10:19:08 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.Vector;


/**
 * This class encapsulates the Message meta data and unique identifier (qos) of a unSubscribe() message.
 * <p />
 * A full specified <b>unSubscribe</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class UnSubscribeQosWrapper extends QosWrapper
{
   private String ME = "UnSubscribeQosWrapper";


   /**
    * Constructor for default qos (quality of service).
    */
   public UnSubscribeQosWrapper()
   {
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
      sb.append("</qos>");
      return sb.toString();
   }
}
