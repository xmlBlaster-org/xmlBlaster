/*------------------------------------------------------------------------------
Name:      I_MsgQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing QoS (quality of service) of publish() and update(). 
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @author ruff@swand.lake.de
 */
public interface I_MsgQosFactory
{
   /**
    * Parses the given Qos and returns a MsgQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param e.g. the XML based ASCII string
    */
   MsgQosData readObject(String xmlQos) throws XmlBlasterException;

   /**
    * Serialize the given data object.  
    * <br>
    * @param The data object to serialize
    * @param extraOffset Formatting hints
    * @return The serialized representation
    */
   String writeObject(MsgQosData msgQosData, String extraOffset);

   /** A human readable name of this factory */
   String getName();
}
