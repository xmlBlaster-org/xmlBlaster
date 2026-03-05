/*------------------------------------------------------------------------------
Name:      I_ConnectQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
//import org.xmlBlaster.util.Global.FactoryType;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing QoS (quality of service) of connect(). 
 * @author xmlBlaster@marcelruff.info
 */
public interface I_ConnectQosFactory
{
   // public static final ConnectQosSaxFactory connectQosSaxFactory = new ConnectQosSaxFactory(Global.instance());
   
   /**
    * Parses the given Qos and returns a ConnectQosData holding the data. 
    * Parsing of connect() QoS is supported here.
    * <pre>
    * java HelloWorld3 -qosFormat json
    * </pre>
    * @param qos e.g. the based ASCII string
    */
   ConnectQosData readObject(String qos) throws XmlBlasterException;
   
   /**
    * Return a factory parsing QoS XML strings from connect() and connect-return messages.
   public static I_ConnectQosFactory getConnectQosSaxFactory() {
      if (connectQosSaxFactory == null) {
         synchronized (I_ConnectQosFactory.class) {
            if (connectQosSaxFactory == null) {
               connectQosSaxFactory = new ConnectQosSaxFactory(Global.instance());
            }
         }
      }
      return connectQosSaxFactory;
   }
    */
   
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
//      if (JacksonUtils.isJson(qos)) {
//         return new ConnectQosJsonFactory(global).readObject(qos);
//         // return global.getConnectQosFactory(FactoryType.JACKSON).readObject(qos);
//      }
//      else if (JacksonUtils.isXML(qos)) {
//         // return new ConnectQosSaxFactory(global).readObject(qos);
//         // return global.getConnectQosSaxFactory().readObject(qos);
//         return new ConnectQosSaxFactory(global).readObject(qos);
//         // return global.getConnectQosFactory(FactoryType.SAX).readObject(qos);
//      }
//      else {
//         throw new XmlBlasterException(global, ErrorCode.INTERNAL_NOTIMPLEMENTED, "I_ConnectQosFactory qos type");
//      }
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
