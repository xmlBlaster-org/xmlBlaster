/*------------------------------------------------------------------------------
Name:      I_ConnectQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

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
    * @param xmlQos e.g. the XML based ASCII string
    */
   ConnectQosData readObject(String xmlQos) throws XmlBlasterException;

   /**
    * Serialize the given data object.  
    * <br>
    * @param qosData The data object to serialize
    * @param extraOffset Formatting hints
    * @return The serialized representation
    */
   String writeObject(ConnectQosData qosData, String extraOffset);

   /**
    * Serialize the given data object.  
    * <br>
    * @param qosData The data object to serialize
    * @param extraOffset Formatting hints
    * @param flag For example Constants.TOXML_FLAG_NOSECURITY
    * @return The serialized representation
    */
   String writeObject(ConnectQosData qosData, String extraOffset, int flag);

   /** A human readable name of this factory */
   String getName();
}
