/*------------------------------------------------------------------------------
Name:      I_QueryKeyFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing key (quality of service) of subscribe(), get(), unSubscribe() and erase(). 
 * @see org.xmlBlaster.util.key.QueryKeyData
 * @see org.xmlBlaster.test.classtest.key.QueryKeyFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public interface I_QueryKeyFactory
{
   /**
    * Parses the given Key and returns a QueryKeyData holding the data. 
    * Parsing of update() and publish() key is supported here.
    * @param e.g. the XML based ASCII string
    */
   QueryKeyData readObject(String xmlKey) throws XmlBlasterException;

   /**
    * Serialize the given data object.  
    * <br>
    * @param The data object to serialize
    * @param extraOffset Formatting hints
    * @return The serialized representation
    */
   String writeObject(QueryKeyData queryKeyData, String extraOffset);

   /** A human readable name of this factory */
   String getName();
}
