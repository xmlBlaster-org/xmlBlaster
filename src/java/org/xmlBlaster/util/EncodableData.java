/*------------------------------------------------------------------------------
Name:      EncodableData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one client property of QosData
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.UnsupportedEncodingException;

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
 *&lt;clientProperty name='myText' type='String' encoding='base64' charset='cp1252'>Hello&lt;/clientProperty>
 * </pre>
 * If the attribute <code>type</code> is missing we assume a 'String' property
 * <p />
 * The encoding charset must be "UTF-8", it can be locally overwritten for base64 encoded strings
 *
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.clientProperty.html">The client.qos.clientProperty requirement</a>
 */
public class EncodableData implements java.io.Serializable, Cloneable
{
   private static final long serialVersionUID = 1320428639197394397L;
   protected String ME = "EncodableData";
   private final String name;
   private String type;
   /** The value encoded as specified with encoding */
   private String value;
   private String encoding;
   /** Mark the charset for a base64 encoded String */
   private String charset;
   /** Needed for Base64 encoding */
   public static final boolean isChunked = false;
   protected String tagName;
   private long size = -1L;
   private boolean forceCdata = false;

   /**
    * @param name  The unique property key
    * @param type The data type of the value, Constants.TYPE_INT etc.
    * @param encoding Constants.ENCODING_NONE=null or Constants.ENCODING_BASE64="base64"
    */
   public EncodableData(String tagName, String name, String type, String encoding) {
      this.tagName = tagName;
      this.name = name;
      this.type = type;
      this.encoding = encoding;
   }

   /**
    * @param name  The unique property key
    * @param type The data type of the value, Constants.TYPE_INT etc.
    * @param encoding Constants.ENCODING_NONE=null or Constants.ENCODING_BASE64="base64"
    */
   public EncodableData(String tagName, String name, String type, String encoding, String value) {
      this.name = name;
      this.tagName = tagName;
      this.type = type;
      this.encoding = encoding;
      setValue(value);
   }

   /**
    * Set binary data, will be of type "byte[]" and base64 encoded
    * @param name  The unique property key
    * @param value The binary data (will instantly be base64 encoded)
    */
   public EncodableData(String tagName, String name, byte[] value) {
      this.name = name;
      this.tagName = tagName;
      this.size = value.length;
      setValue(value);
   }

   public String getName() {
      return this.name;
   }

   public String getType() {
      return this.type;
   }

   public boolean isStringType() {
      return this.type == null || "String".equalsIgnoreCase(this.type) || this.type.length() < 1; // Constants.TYPE_STRING
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
    * The string representation of the value.
    * <p /> 
    * If the string is base64 encoded with a given charset, it is decoded
    * and transformed to the default charset, typically "UTF-8"
    * @return The value which is decoded (readable) in case it was base64 encoded, can be null
    */
   public String getStringValue() {
      if (this.value == null) return null;
      if (Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         byte[] content = Base64.decodeBase64(this.value.getBytes());
         if (getCharset() != null) {
            try {
               return new String(content, getCharset());
            } catch (UnsupportedEncodingException e) {
               e.printStackTrace();
            }
         }
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
    * @return Returns the charset, for example "cp1252" or "UTF-8", helpful if base64 encoded
    */
   public String getCharset() {
      return this.charset;
   }

   /**
    * @param charset The charset to set.
    */
   public void setCharset(String charset) {
      this.charset = charset;
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
         if (this.encoding == null || !this.encoding.equalsIgnoreCase(Constants.ENCODING_FORCE_PLAIN))
            getValidatedValueForXml();
      }
   }

   private String getValidatedValueForXml() {
      if (this.value == null) return "";
      // TODO this has to be more generic
      if (!Constants.ENCODING_BASE64.equalsIgnoreCase(this.encoding)) {
         if (this.value.indexOf("<") != -1 ||
               this.value.indexOf("&") != -1 ||
               this.value.indexOf("|") != -1 ||
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
    */
   public final String toXml(String offset) {
      return toXml(offset, null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @param tmpTagName the tag name to be used for this output. If you
    * specify 'null' the default will be used, i.e. what has been passed in the constructor.
    * @return internal state of the EncodableData as a XML ASCII string
    */
   public final String toXml(String extraOffset, String tmpTagName) {
      return toXml(extraOffset, tmpTagName, false);
   }
   
   /**
    * You may set forceReadable==true to have nicer human readable output.
    * For normal processing leave forceReadable==false. 
    * @param extraOffset The indenting prefix
    * @param tmpTagName If null the default is chosen
    * @param forceReadable If true the base64 is decoded to a 'readable' string
    * @return
    */
   public final String toXml(String extraOffset, String tmpTagName, boolean forceReadable) {
      if (tmpTagName == null)
         tmpTagName = this.tagName;
      
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<").append(tmpTagName);
      if (getName() != null) {
         sb.append(" name='").append(getName()).append("'");
      }
      if (getSize() >= 0) {
         sb.append(" size='").append(getSize()).append("'");
      }
      if (getType() != null) {
         sb.append(" type='").append(getType()).append("'");
      }

      if (forceReadable) {
         String val = getStringValue();
         if (val != null) {
            if (val.indexOf("<") != -1 ||
                val.indexOf("&") != -1) {
               sb.append(">");
               sb.append("<![CDATA[");
               sb.append(val);
               sb.append("]]>");
               sb.append("</").append(tmpTagName).append(">");
               return sb.toString();
            }
            else if (val.indexOf("]]>") != -1) {
               // readable is not possible
               // fall thru
            }
            else {
               sb.append(">");
               sb.append(val);
               sb.append("</").append(tmpTagName).append(">");
               return sb.toString();
            }
         }
      }
      
      if (getEncoding() != null) {
         sb.append(" encoding='").append(getEncoding()).append("'");
      }
      if (getCharset() != null) {
         sb.append(" charset='").append(getCharset()).append("'");
      }

      String val = getValueRaw();
      if (val == null)
         sb.append("/>");
      else {
         sb.append(">");
         if (this.forceCdata) sb.append("<![CDATA[");
         sb.append(val);
         if (this.forceCdata) sb.append("]]>");
         sb.append("</").append(tmpTagName).append(">");
      }

      return sb.toString();
   }

   public static void main(String[] args) {
      if (args.length == 0) {
         System.err.println("Usage: java org.xmlBlaster.util.EncodableData base64");
         System.err.println("Usage: java org.xmlBlaster.util.EncodableData -e literal");
         System.exit(1);
      }
      if (args.length == 1) {
         byte[] content = Base64.decodeBase64(args[0].getBytes());
         System.out.println("'" + args[0] + "' -> '" + new String(content) + "'");
      }
      else {
         byte[] content = Base64.encodeBase64(args[1].getBytes(), false);
         System.out.println("'" + args[1] + "' -> '" + new String(content) + "'");
      }
      
   }
}
