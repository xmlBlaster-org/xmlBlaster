/*------------------------------------------------------------------------------
Name:      I_StatusQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;


/**
 * Parsing/Serializing QoS (quality of service) of publish() and update().
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public interface I_StatusQosFactory
{
   /**
    * Parses the given Qos and returns a StatusQosData holding the data.
    * Parsing of update() and publish() QoS is supported here.
    * @param e.g. the XML based ASCII string
    */
   StatusQosData readObject(String xmlQos) throws XmlBlasterException;

   /**
    * Parses the given Qos and returns a StatusQos holding the data. 
    * 
    * <pre>
    * java HelloWorld3 -qosFormat json
    * </pre>
    * @param global current instance of global, will be initialized if null
    * @param qos e.g. the XML/JSON based ASCII string or JSON
    */
   public static StatusQosData parse(Global global, String qos) throws XmlBlasterException {
      return parse(global, qos, null);
   }
   
   /**
    * Parses the given Qos and returns a StatusQos holding the data. 
    * 
    * <pre>
    * java HelloWorld3 -qosFormat json
    * </pre>
    * @param global current instance of global, will be initialized if null
    * @param qos e.g. the XML/JSON based ASCII string or JSON
    * @param methodName used to determine, whether quickparsefactory or SAX 
    *                   factory should be returned, if serialData is XML

    */
   public static StatusQosData parse(Global global, String qos, MethodName methodName) throws XmlBlasterException {
      if (global == null) {
         global = Global.instance();
      }
      I_StatusQosFactory factory = global.getStatusQosFactory(qos, methodName);
      return factory.readObject(qos);
   }

   /**
    * Serialize the given data object.
    * <br>
    * @param The data object to serialize
    * @param extraOffset Formatting hints
    * @param props Formatting hints (see Constants.TOXML_*)
    * @return The serialized representation
    */
   String writeObject(StatusQosData statusQosData, String extraOffset, Properties props);

   String writeObject(StatusQosData statusQosData, String extraOffset, Properties props, boolean dumpClientProperties);

   /** A human readable name of this factory */
   String getName();
}
