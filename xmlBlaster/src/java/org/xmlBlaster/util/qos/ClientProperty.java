/*------------------------------------------------------------------------------
Name:      ClientProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one client property of QosData
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.apache.commons.codec.binary.Base64;

/**
 * This class encapsulates one client property in a QoS. 
 * <p/>
 * Examples:
 * <pre>
 *&lt;clientProperty name='transactionId' type='int' encoding=''>
     120001
 *&lt;/clientProperty>
 *&lt;clientProperty name='transactionId' type='String' encoding='base64'>
     OKFKAL==
 *&lt;/clientProperty>
 * </pre>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.clientProperty.html">The client.qos.clientProperty requirement</a>
 * @see org.xmlBlaster.test.classtest.ClientPropertyTest
 * @see org.xmlBlaster.test.qos.TestClientProperty
 */
public final class ClientProperty implements java.io.Serializable, Cloneable
{
   private final String ME = "ClientProperty";
   private final transient Global glob;
   private final String name;
   private final String type;
   private String value;
   private String encoding;

   /**
    * @param name  The unique property key
    * @param type The data type of the value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    */
   public ClientProperty(Global glob, String name, String type, String encoding) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.name = name;
      this.type = type;
      this.encoding = encoding;
   }

   public String getName() {
      return this.name;
   }

   public String getType() {
      return this.type;
   }

   /**
    * The raw still encoded value
    */
   public String getValueRaw() {
      return this.value;
   }

   /**
    * @return Can be null
    */
   public String getStringValue() {
      if (this.value == null) return null;
      if (Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         byte[] content = Base64.decodeBase64(this.value.getBytes());
         return new String(content);
      }
      return this.value;
   }

   /*
   public String getStringValue() {
      Object tmp = this.value;
      if (Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         byte[] content = null;
         if (value instanceof byte[])
            content = Base64.decodeBase64((byte[])this.value);
         else
            content = Base64.decodeBase64(this.value.toString().getBytes());
         tmp = convertPropertyObject(this.type, new String(content));
      }
      return tmp.toString();
   }
   */

   public int getIntValue() {
      return ((Integer)convertPropertyObject(Constants.TYPE_INT, getStringValue())).intValue();
   }

   public String getEncoding() {
      return this.encoding;
   }

   /**
    * Set the value, it will be encoded with the encoding specified in the constructor. 
    */
   public void setValue(String value) {
      setValue(value, this.encoding);
   }

   /**
    * Set the already correctly encoded value
    */
   public void setValueRaw(String value) {
      this.value = value;
   }

   /**
    * Set the real value which will be encoded as specified. 
    * Currently only base64 is supported
    * @param value The not encoded value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    */
   public void setValue(String value, String encoding) {
      this.encoding = encoding;
      if (value == null) {
         this.value = null;
         return;
      }
      if (Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         this.encoding = Constants.ENCODING_BASE64; // correct case sensitive
         boolean isChunked = false;
         byte[] content = Base64.encodeBase64(value.getBytes(), isChunked);
         this.value = new String(content);
      }
      else
         this.value = value;
   }

   private String getValidatedValueForXml() {
      if (this.value == null) return "";
      if (!Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         if (this.value.indexOf("<") != -1 ||
             this.value.indexOf("]]>") != -1) {
            // Force base64 encoding
            setValue(this.value, Constants.ENCODING_BASE64);
         }
      }
      return this.value;
   }

   /**
    * Helper which returns the type of the object as a string
    * @param val
    * @return
    */
   public final static String getPropertyType(Object val) {
      if (val == null) return null;
      if (val instanceof String) return null; // default
      if (val instanceof Boolean) return Constants.TYPE_BOOLEAN;
      if (val instanceof Byte) return Constants.TYPE_BYTE;
      if (val instanceof Double) return Constants.TYPE_DOUBLE;
      if (val instanceof Float) return Constants.TYPE_FLOAT;
      if (val instanceof Integer) return Constants.TYPE_INT;
      if (val instanceof Long) return Constants.TYPE_LONG;
      if (val instanceof Short) return Constants.TYPE_SHORT;
      return null; 
   }

   /**
    * Helper to convert 'val' to the given object of type 'type'. 
    * @param type the type of the object
    * @param val the object itself
    * @return null if the type is unrecognized, a String object if the
    * type is null (implicitly String) or the correct type if a mapping has been found.
    */
   public final static Object convertPropertyObject(String type, String val) {
      if (type == null) return val;
      if (Constants.TYPE_BOOLEAN.equalsIgnoreCase(type)) return new Boolean(val);
      if (Constants.TYPE_BYTE.equalsIgnoreCase(type)) return new Byte(val);
      if (Constants.TYPE_DOUBLE.equalsIgnoreCase(type)) return new Double(val);
      if (Constants.TYPE_FLOAT.equalsIgnoreCase(type)) return new Float(val);
      if (Constants.TYPE_INT.equalsIgnoreCase(type)) return new Integer(val);
      if (Constants.TYPE_SHORT.equalsIgnoreCase(type)) return new Short(val);
      if (Constants.TYPE_LONG.equalsIgnoreCase(type)) return new Long(val);
      return null; 
   }
   
   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the ClientProperty as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<clientProperty");
      if (getName() != null) {
         sb.append(" name='").append(getName()).append("'");
      }
      if (getType() != null) {
         sb.append(" type='").append(getType()).append("'");
      }
      if (getEncoding() != null) {
         sb.append(" encoding='").append(getEncoding()).append("'");
      }
      sb.append(">");
      sb.append(getValidatedValueForXml());
      sb.append("</clientProperty>");

      return sb.toString();
   }
}
