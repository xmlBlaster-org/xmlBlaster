/*------------------------------------------------------------------------------
Name:      I_ConnectQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.QosFormatEnum;
//import org.xmlBlaster.util.Global.FactoryType;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing QoS (quality of service) of connect(). 
 * @author xmlBlaster@marcelruff.info
 */
public interface I_ConnectQosFactory
{

   /**
    * Parses the given Qos and returns a ConnectQosData holding the data. 
    * Parsing of connect() QoS is supported here.
    * <pre>
    * java HelloWorld3 -qosFormat json
    * </pre>
    * @param qos e.g. the based ASCII string
    */
   ConnectQosData readObject(String qos) throws XmlBlasterException;

   QosFormatEnum getQosFormat();
   
   /**
    * Parses the given Qos and returns a ConnectQosData holding the data. 
    * Parsing of connect() QoS is supported here.
    * <pre>
    * java HelloWorld3 -qosFormat json
    * </pre>
    * @param global current instance of global, will be initialized if null
    * @param qos e.g. the XML/JSON based ASCII string or JSON
    */
   public static ConnectQosData parse(Global global, String qos) throws XmlBlasterException {
      if (global == null)
         global = Global.instance();
      I_ConnectQosFactory factory = global.getConnectQosFactory(qos);
      return factory.readObject(qos);
   }

   /**
    * Serialize the given data object.  
    * <br>
    * @param qosData The data object to serialize
    * @param extraOffset Formatting hints
    * @param flag For example Constants.TOXML_FLAG_NOSECURITY
    * @return The serialized representation
    */
   String writeObject(ConnectQosData qosData, String extraOffset, Properties props);

   /** A human readable name of this factory */
   String getName();
}
