/*------------------------------------------------------------------------------
Name:      EncodableData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one client property of QosData
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.apache.commons.codec.binary.Base64;

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
 */
public class EncodableData implements java.io.Serializable, Cloneable
{
   protected String ME = "EncodableData";
   private final transient Global glob;
   private final String name;
   private String type;
   /** The value encoded as specified with encoding */
   private String value;
   private String encoding;
   /** Needed for Base64 encoding */
   public static final boolean isChunked = false;
   protected String tagName;
   private long size = -1L;
   private boolean forceCdata = false;

   /**
    * @param name  The unique property key
    * @param type The data type of the value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    */
   public EncodableData(Global glob, String tagName, String name, String type, String encoding) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.tagName = tagName;
      this.name = name;
      this.type = type;
      this.encoding = encoding;
   }

   /**
    * @param name  The unique property key
    * @param type The data type of the value
    * @param encoding null or Constants.ENCODING_BASE64="base64"
    */
   public EncodableData(Global glob, String tagName, String name, String type, String encoding, String value) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.name = name;
      this.tagName = tagName;
      this.type = type;
      this.encoding = encoding;
      setValue(value);
   }

   /**
    * Set binary data, will be of type "byte[]" and base64 encoded
    * @param name  The unique property key
    * @param value The binary data
    */
   public EncodableData(Global glob, String tagName, String name, byte[] value) {
      this.glob = (glob == null) ? Global.instance() : glob;
      this.name = name;
      this.tagName = tagName;
      setValue(value);
   }

   public String getName() {
      return this.name;
   }

   public String getType() {
      return this.type;
   }

   public boolean isStringType() {
      return this.type == null || "String".equalsIgnoreCase(this.type); // Constants.TYPE_STRING
   }

   /**
    * The real, raw content size (not the base64 size)
    * @return -1 if not set
    */
   public long getSize() {
      return this.size;
   }

   public void forceCdata(boolean forceCdata) {
      this.forceCdata = forceCdata;
   }

   /**
    * The real, raw content size (not the base64 size)
    * @param size If set >= 0 force to dump the size attribute
    */
   public void setSize(long size) {
      this.size = size;
   }

   public boolean isBase64() {
      return Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding);
   }

   /**
    * The raw still encoded value
    */
   public String getValueRaw() {
      return this.value;
   }

   /**
    * @return The value which is decoded (readable) in case it was base64 encoded, can be null
    */
   public String getStringValue() {
      if (this.value == null) return null;
      if (Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         byte[] content = Base64.decodeBase64(this.value.getBytes());
         return new String(content);
      }
      return this.value;
   }

   /**
    * @return Can be null
    */
   public byte[] getBlobValue() {
      if (this.value == null) return null;
      if (Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         byte[] content = Base64.decodeBase64(this.value.getBytes());
         return content;
      }
      return this.value.getBytes();
   }

   /**
    * @exception NumberFormatException
    */
   public int getIntValue() {
      return (new Integer(getStringValue())).intValue();
   }

   public boolean getBooleanValue() {
      return (new Boolean(getStringValue())).booleanValue();
   }

   /**
    * @exception NumberFormatException
    */
   public double getDoubleValue() {
      return (new Double(getStringValue())).doubleValue();
   }

   /**
    * @exception NumberFormatException
    */
   public float getFloatValue() {
      return (new Float(getStringValue())).floatValue();
   }

   public byte getByteValue() {
      return (new Byte(getStringValue())).byteValue();
   }

   /**
    * @exception NumberFormatException
    */
   public long getLongValue() {
      return (new Long(getStringValue())).longValue();
   }

   /**
    * @exception NumberFormatException
    */
   public short getShortValue() {
      return (new Short(getStringValue())).shortValue();
   }

   /**
    * Depending on the type we return a Float, Long, Integer, ...
    */
   public Object getObjectValue() {
      return convertPropertyObject(this.type, getStringValue());
   }

   /**
    * @return For example Constants.TYPE_INTEGER="int" or Constants.TYPE_BLOB="byte[]"
    */
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
    * Set binary data, will be of type "byte[]" and base64 encoded
    */
   public void setValue(byte[] value) {
      this.type = Constants.TYPE_BLOB;
      this.encoding = Constants.ENCODING_BASE64;
      byte[] content = Base64.encodeBase64(value, isChunked);
      this.value = new String(content);
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
         byte[] content = Base64.encodeBase64(value.getBytes(), isChunked);
         this.value = new String(content);
      }
      else {
         this.value = value;
         getValidatedValueForXml();
      }
   }

   private String getValidatedValueForXml() {
      if (this.value == null) return "";
      if (!Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         if (this.value.indexOf("<") != -1 ||
             this.value.indexOf("&") != -1 ||
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
      if (val instanceof byte[]) return Constants.TYPE_BLOB;
      return null; 
   }

   /**
    * Helper to convert 'val' to the given object of type 'type'. 
    * @param type the type of the object
    * @param val the object itself
    * @return null if the type is unrecognized or the value is null; 
    *         A String object if the type is null (implicitly String)
    *         or the correct type (for example Float) 
    *         if a mapping has been found.
    */
   public final static Object convertPropertyObject(String type, String val) {
      if (type == null) return val;
      if (val == null) return null;
      if (Constants.TYPE_BOOLEAN.equalsIgnoreCase(type)) return new Boolean(val);
      if (Constants.TYPE_BYTE.equalsIgnoreCase(type)) return new Byte(val);
      if (Constants.TYPE_DOUBLE.equalsIgnoreCase(type)) return new Double(val);
      if (Constants.TYPE_FLOAT.equalsIgnoreCase(type)) return new Float(val);
      if (Constants.TYPE_INT.equalsIgnoreCase(type)) return new Integer(val);
      if (Constants.TYPE_SHORT.equalsIgnoreCase(type)) return new Short(val);
      if (Constants.TYPE_LONG.equalsIgnoreCase(type)) return new Long(val);
      if (Constants.TYPE_BLOB.equalsIgnoreCase(type)) return val.getBytes();
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
    * @return internal state of the EncodableData as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<").append(tagName);
      if (getName() != null) {
         sb.append(" name='").append(getName()).append("'");
      }
      if (getSize() >= 0) {
         sb.append(" size='").append(getSize()).append("'");
      }
      if (getType() != null) {
         sb.append(" type='").append(getType()).append("'");
      }
      if (getEncoding() != null) {
         sb.append(" encoding='").append(getEncoding()).append("'");
      }

      //sb.append(getValidatedValueForXml());
      String val = getValueRaw();
      if (val == null)
         sb.append("/>");
      else {
         sb.append(">");
         if (this.forceCdata) sb.append("<![CDATA[");
         sb.append(val);
         if (this.forceCdata) sb.append("]]>");
         sb.append("</").append(this.tagName).append(">");
      }

      return sb.toString();
   }
}
