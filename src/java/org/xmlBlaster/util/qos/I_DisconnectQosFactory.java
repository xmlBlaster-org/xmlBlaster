/*------------------------------------------------------------------------------
Name:      I_DisconnectQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing QoS (quality of service) of disconnect(). 
 * @author xmlBlaster@marcelruff.info
 */
public interface I_DisconnectQosFactory
{
   /**
    * Parses the given Qos and returns a DisconnectQosData holding the data. 
    * Parsing of disconnect() QoS is supported here.
    * @param xmlQos e.g. the XML based ASCII string
    */
   DisconnectQosData readObject(String xmlQos) throws XmlBlasterException;

   /**
    * Serialize the given data object.  
    * <br>
    * @param qosData The data object to serialize
    * @param extraOffset Formatting hints
    * @return The serialized representation
    */
   String writeObject(DisconnectQosData qosData, String extraOffset);

   /** A human readable name of this factory */
   String getName();
}
