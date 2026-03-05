/*------------------------------------------------------------------------------
Name:      I_MsgQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing QoS (quality of service) of publish() and update(). 
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public interface I_MsgQosFactory
{

   /**
    * Parses the given Qos and returns a MsgQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param qos e.g. the XML/JSON based ASCII string
    */
   MsgQosData readObject(String qos) throws XmlBlasterException;

   /**
    * Parses the given Qos and returns a MsgQosData holding the data. 
    * 
    * <pre>
    * java HelloWorld3 -qosFormat json
    * </pre>
    * @param global current instance of global, will be initialized if null
    * @param qos e.g. the XML based ASCII string or JSON
    */
   public static MsgQosData parse(Global global, String qos) throws XmlBlasterException {
      if (global == null) {
         global = Global.instance();
      }
      I_MsgQosFactory factory = global.getMsgQosFactory(qos);
      return factory.readObject(qos);
   }

   /**
    * Serialize the given data object.  
    * <br>
    * @param The data object to serialize
    * @param extraOffset Formatting hints
    * @param props Formatting hints (like "forceReadable" to be human readable) 
    * @return The serialized representation
    */
   String writeObject(MsgQosData msgQosData, String extraOffset, Properties props);
   
   /** A human readable name of this factory */
   String getName();
}
