/*------------------------------------------------------------------------------
Name:      I_QueryQosFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing QoS (quality of service) of publish() and update(). 
 * @see org.xmlBlaster.test.classtest.qos.QueryQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public interface I_QueryQosFactory
{
   /**
    * Parses the given Qos and returns a QueryQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param e.g. the XML based ASCII string
    */
   QueryQosData readObject(String xmlQos) throws XmlBlasterException;

   /**
    * Serialize the given data object.  
    * <br>
    * @param The data object to serialize
    * @param extraOffset Formatting hints
    * @return The serialized representation
    */
   String writeObject(QueryQosData queryQosData, String extraOffset);

   /** A human readable name of this factory */
   String getName();
}
