/*------------------------------------------------------------------------------
Name:      ClientProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one client property of QosData
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.EncodableData;
import org.xmlBlaster.util.Global;

/**
 * This class encapsulates one client property in a QoS. 
 * <p/>
 * Examples:
 * <pre>
 *&lt;clientProperty name='transactionId' type='int'>120001&lt;/clientProperty>
 *&lt;clientProperty name='myKey'>Hello World&lt;/clientProperty>
 *&lt;clientProperty name='myBlob' type='byte[]' encoding='base64'>OKFKAL==&lt;/clientProperty>
 * </pre>
 * If the attribute <code>type</code> is missing we assume a 'String' property
 *
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.clientProperty.html">The client.qos.clientProperty requirement</a>
 * @see org.xmlBlaster.test.classtest.ClientPropertyTest
 * @see org.xmlBlaster.test.qos.TestClientProperty
 */
public final class ClientProperty extends EncodableData
{
   /**
    * @param name  The unique property key
    * @param type The data type of the value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    */
   public ClientProperty(Global glob, String name, String type, String encoding) {
      super("clientProperty", name, type, encoding);
      ME = "ClientProperty";
   }

   /**
    * @param name  The unique property key
    * @param type The data type of the value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    */
   public ClientProperty(Global glob, String name, String type, String encoding, String value) {
      super("clientProperty", name, type, encoding, value);
      ME = "ClientProperty";
   }

   /**
    * Set binary data, will be of type "byte[]" and base64 encoded
    * @param name  The unique property key
    * @param value The binary data
    */
   public ClientProperty(Global glob, String name, byte[] value) {
      super("clientProperty", name, value);
      ME = "ClientProperty";
   }

   public String toString() {
      return getStringValue();
   }
}
