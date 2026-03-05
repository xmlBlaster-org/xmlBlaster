/*------------------------------------------------------------------------------
Name:      I_DisconnectQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.Properties;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;


/**
 * Parsing/Serializing QoS (quality of service) of disconnect(). 
 * @author xmlBlaster@marcelruff.info
 */
public interface I_DisconnectQosFactory
{
   /**
    * Parses the given Qos and returns a DisconnectQosData holding the data. 
    * Parsing of disconnect() QoS is supported here.
    * @param qos e.g. the XML/JSON based ASCII string
    */
   DisconnectQosData readObject(String qos) throws XmlBlasterException;

   /**
    * Parses the given Qos and returns a DisconnectQosData holding the data. 
    * 
    * <pre>
    * java HelloWorld3 -qosFormat json
    * </pre>
    * @param global current instance of global, will be initialized if null
    * @param qos e.g. the XML based ASCII string or JSON
    */
   public static DisconnectQosData parse(Global global, String qos) throws XmlBlasterException {
      if (global == null) {
         global = Global.instance();
      }
      I_DisconnectQosFactory factory = global.getDisconnectQosFactory(qos);
      return factory.readObject(qos);
   }

   /**
    * Serialize the given data object.  
    * <br>
    * @param qosData The data object to serialize
    * @param extraOffset Formatting hints
    * @return The serialized representation
    */
   String writeObject(DisconnectQosData qosData, String extraOffset, Properties props);

   /** A human readable name of this factory */
   String getName();
}
