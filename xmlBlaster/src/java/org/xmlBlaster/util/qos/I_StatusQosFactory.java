/*------------------------------------------------------------------------------
Name:      I_StatusQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing QoS (quality of service) of publish() and update().
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author ruff@swand.lake.de
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
    * Serialize the given data object.
    * <br>
    * @param The data object to serialize
    * @param extraOffset Formatting hints
    * @return The serialized representation
    */
   String writeObject(StatusQosData statusQosData, String extraOffset);

   /** A human readable name of this factory */
   String getName();
}
