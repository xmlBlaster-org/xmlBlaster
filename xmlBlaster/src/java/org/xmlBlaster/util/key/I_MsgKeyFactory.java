/*------------------------------------------------------------------------------
Name:      I_MsgKeyFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.key;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * Parsing/Serializing key (quality of service) of publish() and update(). 
 * @see org.xmlBlaster.util.key.MsgKeyData
 * @see org.xmlBlaster.test.classtest.key.MsgKeyFactoryTest
 * @author xmlBlaster@marcelruff.info
 */
public interface I_MsgKeyFactory
{
   /**
    * Parses the given Key and returns a MsgKeyData holding the data. 
    * Parsing of update() and publish() key is supported here.
    * @param e.g. the XML based ASCII string
    */
   MsgKeyData readObject(String xmlKey) throws XmlBlasterException;

   /**
    * Serialize the given data object.  
    * <br>
    * @param The data object to serialize
    * @param extraOffset Formatting hints
    * @return The serialized representation
    */
   String writeObject(MsgKeyData msgKeyData, String extraOffset);

   /** A human readable name of this factory */
   String getName();
}
