/*------------------------------------------------------------------------------
Name:      QosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS message
Version:   $Id: QosWrapper.java,v 1.4 2000/06/18 15:21:59 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.Log;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * This base class encapsulates XmlQoS which you send to xmlBlaster.
 * <p />
 * A typical minimal qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *     &lt;/qos>
 * </pre>
 * <br />
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class QosWrapper
{
   private String ME = "QosWrapper";


   /**
    * Constructs this base object
    */
   public QosWrapper()
   {
   }


   /**
    * Converts the data in XML ASCII string.
    * <p />
    * This is the minimal key representation.<br />
    * You should provide your own toString() method.
    * @return An XML ASCII string
    */
   public String toString()
   {
      StringBuffer sb = new StringBuffer();
      sb.append("<qos>\n");
      sb.append("</qos>");
      return sb.toString();
   }
}
