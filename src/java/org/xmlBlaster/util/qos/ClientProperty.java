/*------------------------------------------------------------------------------
Name:      ClientProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one client property of QosData
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.io.IOException;

import org.xmlBlaster.util.EncodableData;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

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
   private static final long serialVersionUID = 6415499809321164696L;
   /** Typically used tag name for plugin attributes */
   public static final String ATTRIBUTE_TAG = "attribute";
   /** Typicall used tag name for subscribeQos and other Qos */
   public static final String CLIENTPROPERTY_TAG = "clientProperty";

   /**
    * @param name  The unique property key
    * @param type The data type of the value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    * @deprecated you should use the constructors with no global
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
   public ClientProperty(String name, String type, String encoding) {
      super("clientProperty", name, type, encoding);
      ME = "ClientProperty";
   }

   /**
    * @param name  The unique property key
    * @param type The data type of the value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    * @deprecated you should use the alternative with no global.
    */
   public ClientProperty(Global glob, String name, String type, String encoding, String value) {
      super("clientProperty", name, type, encoding, value);
      ME = "ClientProperty";
   }

   /**
    * @param name  The unique property key
    * @param type The data type of the value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    * @param value The original value (not yet encoded!)
    */
   public ClientProperty(String name, String type, String encoding, String value) {
      super("clientProperty", name, type, encoding, value);
      ME = "ClientProperty";
   }

   /**
    * Set binary data, will be of type "byte[]" and base64 encoded
    * @param name  The unique property key
    * @param value The binary data
    */
   public ClientProperty(String name, byte[] value) {
      super("clientProperty", name, value);
      ME = "ClientProperty";
   }

   public ClientProperty(String name, boolean value) {
      this(name, Constants.TYPE_BOOLEAN, Constants.ENCODING_NONE, ""+value);
      ME = "ClientProperty";
   }

   public String toString() {
      return getStringValue();
   }
   
   public static ClientProperty parseCompactClientProperties(Global glob, JsonParser parser) throws XmlBlasterException {
      try {
         if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                  "Expected START_OBJECT for compact clientProperties");
         }

         String key = null;
         String type = null;
         String value = null;
         String encoding = null;
         while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();

            if (fieldName == null) {
               throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                     "Missing key in compact clientProperty entry");
            }

            // Move to value:
            JsonToken valueToken = parser.nextToken();

            if ("encoding".equals(fieldName)) {
               if (valueToken != JsonToken.VALUE_STRING) {
                  throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE, "'encoding' must be a string");
               }
               encoding = parser.getValueAsString();
            } else {
               key = fieldName;

               switch (valueToken) {
               case VALUE_NUMBER_INT:
                  long lVal = parser.getLongValue();

                  if (lVal >= Integer.MIN_VALUE && lVal <= Integer.MAX_VALUE) {
                     type = "int";
                     value = Integer.toString((int) lVal);
                  } else {
                     type = "long";
                     value = Long.toString(lVal);
                  }
                  break;

               case VALUE_NUMBER_FLOAT:
                  type = "double";
                  value = Double.toString(parser.getDoubleValue());
                  break;

               case VALUE_TRUE:
               case VALUE_FALSE:
                  type = "boolean";
                  value = Boolean.toString(parser.getBooleanValue());
                  break;

               case VALUE_STRING:
                  type = "string";
                  value = parser.getValueAsString();
                  break;

               case VALUE_NULL:
                  type = "string";
                  value = null;
                  break;

               default:
                  throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                        "Unsupported JSON value for compact clientProperty key='" + key + "'");

               }
            }

         }
         return new ClientProperty(key, type, encoding, value);

      } catch (IOException e) {
         throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
               "Failed to parse compact clientProperties: " + e.getMessage());
      }
   }
}
