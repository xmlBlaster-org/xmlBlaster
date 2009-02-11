/*------------------------------------------------------------------------------
Name:      StatusQosQuickParseFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.RcvTimestamp;
import org.xmlBlaster.util.XmlBuffer;
import org.xmlBlaster.util.def.MethodName;

/**
 * Parsing xml QoS (quality of service) of return status. 
 * <p>
 * Here we parse it with normal string operations for better performance. 
 * This is 40 times faster than a SAX parse (6 microsec instead of 241 microsec on a 600 MHz AMD Linux).
 * </p>
 * <p>
 * For the xml representation see StatusQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.StatusQosData
 * @see org.xmlBlaster.util.qos.StatusQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * TODO: implement the parsing from the method name
 */
public class StatusQosQuickParseFactory implements I_StatusQosFactory
{
   private static Logger log = Logger.getLogger(StatusQosQuickParseFactory.class.getName());
   private final Global glob;

   private  StatusQosData statusQosData;

   /**
    * Can be used as singleton. 
    */
   public StatusQosQuickParseFactory(Global glob) {
      this.glob = glob;
   }

   /**
    * Parses the given xml Qos and returns a StatusQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized StatusQosData readObject(String xmlQos) throws XmlBlasterException {
      statusQosData = new StatusQosData(glob, this, xmlQos, MethodName.UNKNOWN);
      if (xmlQos != null && xmlQos.length() > 15) { // "<qos/>" or "<qos></qos>"

         statusQosData.setState(parseOurself(xmlQos, "<state id="));
         statusQosData.setStateInfo(parseOurself(xmlQos, "info="));
         statusQosData.setSubscriptionId(parseOurself(xmlQos, "<subscribe id="));
         statusQosData.setKeyOid(parseOurself(xmlQos, "<key oid="));
         String tmp = parseOurself(xmlQos, "<rcvTimestamp nanos=");
         if (tmp != null) {
            try {
               statusQosData.setRcvTimestamp(new RcvTimestamp(Long.parseLong(tmp)));
            }
            catch(NumberFormatException e) {
               e.printStackTrace();
               log.severe("Invalid rcvTimestamp - nanos =" + tmp);
            }
         }
      }
      return statusQosData;
   }

   /**
    * Parse xml ourself, to gain performance
    * @return The value of the attribute or null if not found
    */
   private final String parseOurself(String str, String token) {
      int index = str.indexOf(token);
      if (index >= 0) {
         int from = index+token.length();
         char apo = str.charAt(from);
         int end = str.indexOf(apo, from+1);
         if (end > 0) {
            return XmlBuffer.unEscapeXml(str.substring(from+1, end));
         }
      }
      return null;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(StatusQosData statusQosData, String extraOffset, Properties props) {
      return StatusQosSaxFactory.writeObject_(statusQosData, extraOffset, props);
   }

   /**
    * A human readable name of this factory
    * @return "StatusQosQuickParseFactory"
    */
   public String getName() {
      return "StatusQosQuickParseFactory";
   }
}
