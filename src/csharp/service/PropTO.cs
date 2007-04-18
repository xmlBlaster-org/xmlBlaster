/*----------------------------------------------------------------------------
Name:      PropTO.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   A generic service approach
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      04/2007
See:       http://www.xmlblaster.org/
-----------------------------------------------------------------------------*/
using System;
using System.Collections.Generic;
using System.Text;
using System.Xml;
using System.Xml.Schema;
using System.Xml.Serialization;

namespace org.xmlBlaster.contrib.service {
   [XmlRootAttribute("prop", IsNullable = false)]
   public class PropTO : IXmlSerializable {

      public static readonly string ENCODING_BASE64 = "base64";

      public static readonly string ENCODING_PLAIN = "";

      public static readonly string KEY_SERVICENAME = "serviceName";

      public static readonly string KEY_QUERYTYPE = "queryType";

      public static readonly string KEY_ENCODING = "encoding";

      public static readonly string KEY_QUERY = "query";

      /** To send update data */
      public static readonly string KEY_DATA = "data";

      /** The encoding type of the data prop, "" or "base64" */
      public static readonly string KEY_DATAENCODING = "dataEncoding";

      public static readonly string VALUE_DATAENCODING_DEFAULT = ENCODING_PLAIN;


      public static readonly string KEY_MIME = "mime";

      public static readonly string KEY_RESULTMIME = "resultMime";

      public static readonly string KEY_RESULTENCODING = "resultEncoding";

      public static readonly string KEY_RESULT = "result";

      /** Data from client will be bounced back, e.g. for requestId */
      public static readonly string KEY_BOUNCE = "bounce";

      public static readonly string VALUE_QUERYTYPE_NAMED = "named";

      public static readonly string VALUE_QUERYTYPE_EXACT = "exact";

      public static readonly string VALUE_QUERYTYPE_XPATH = "xpath";

      public static readonly string VALUE_QUERYTYPE_UPDATE = "update";

      public static readonly string VALUE_SERVICE_BUDDY = "buddy";

      public static readonly string VALUE_SERVICE_TRACK = "track";

      public static readonly string VALUE_RESULTENCODING_PLAIN = "";

      public static readonly string VALUE_RESULTENCODING_DEFAULT = ENCODING_BASE64;

      public static readonly string VALUE_RESULTMIME_PREFIX = "application/xmlBlaster.service";

      public static readonly string PROP = "prop"; // tag name

      public static readonly string KEY = "key"; // attribute name

      protected string key;

      protected string value = "";

      // transient: the encoding is tranfered in another PropTO instance
      protected string encoding;

      public PropTO() {
      }

      public PropTO(string key, string value) {
         this.key = key;
         this.value = value;
      }

      /// <summary>
      /// The value will be send as BASE64 encoded. 
      /// IMPORTANT: You have to set an additional property 
      /// service.addProp(new PropTO(PropTO.KEY_DATAENCODING, PropTO.ENCODING_BASE64));
      /// </summary>
      /// <param name="key"></param>
      /// <param name="value"></param>
      public PropTO(string key, byte[] value) /*throws System.FormatException*/ {
         this.key = key;
         this.value = System.Convert.ToBase64String(value);
         this.encoding = PropTO.ENCODING_BASE64; // transient setting
      }

      public string GetKey() {
         return (this.key == null) ? "" : this.key;
      }

      public void SetKey(string key) {
         this.key = key;
      }

      public string GetValueRaw() {
         return this.value;
      }

      /// <summary>
      /// If it was base64 encoded it is converted as UTF-8 string
      /// </summary>
      /// <returns>Never null</returns>
      public string GetValue() {
         if (this.value == null || this.value.Length < 1) return "";
         if (!isBase64Encoding()) return this.value;

         try {
            byte[] dBytes = System.Convert.FromBase64String(this.value);
            return System.Text.Encoding.UTF8.GetString(dBytes, 0, dBytes.Length);
         }
         catch (System.FormatException) {
            System.Console.WriteLine("Base 64 string length is not " +
                "4 or is not an even multiple of 4.");
            return "";
         }
      }
      /**
       * Returns the raw byte[] of base64 encoding data. If data was of type
       * String it is returned as UTF-8 bytes.
       *
       * @return never null
       */
      public byte[] getValueBytes() {
         if (this.value == null || this.value.Length < 1) return new byte[0];
         if (isBase64Encoding()) {
            return System.Convert.FromBase64String(this.value);
         }
         return System.Text.Encoding.UTF8.GetBytes(this.value);
      }

      public void SetValue(string value) {
         this.value = value;
      }

      public bool isBase64Encoding() {
         if (this.encoding == null) return false;
         return (ENCODING_BASE64.Equals(this.encoding));
      }

      public void SetEncoding(string encoding) {
         this.encoding = encoding;
      }

      public bool getBoolValue() {
         try {
            return Boolean.Parse(this.value);
         }
         catch (System.FormatException e) {
            System.Console.WriteLine("Is not a boolean '" + this.value + "' :" + e.ToString());
            return false;
         }
      }

      public static List<PropTO> ReadSiblings(XmlReader reader) {
         List<PropTO> list = new List<PropTO>();
         bool found = reader.ReadToDescendant(PROP);
         if (found) {
            string queryEncoding = null;
            string resultEncoding = null;
            string dataEncoding = null;
            do {
               PropTO prop = new PropTO();
               reader.MoveToAttribute(KEY);
               prop.SetKey(reader.ReadContentAsString());
               reader.MoveToContent();
               /*This method reads the start tag, the contents of the element, and moves the reader past 
                * the end element tag. It expands entities and ignores processing instructions and comments.
                * The element can only contain simple content. That is, it cannot have child elements.
                */

               if (resultEncoding != null && PropTO.KEY_RESULT.Equals(prop.GetKey())) {
                  prop.SetEncoding(resultEncoding);
               }
               if (queryEncoding != null && PropTO.KEY_QUERY.Equals(prop.GetKey())) {
                  prop.SetEncoding(queryEncoding);
               }
               if (dataEncoding != null && PropTO.KEY_DATA.Equals(prop.GetKey())) {
                  prop.SetEncoding(dataEncoding);
               }

               if (resultEncoding == null && PropTO.KEY_RESULT.Equals(prop.GetKey()) ||
                  queryEncoding == null && PropTO.KEY_QUERY.Equals(prop.GetKey()) ||
                  dataEncoding == null && PropTO.KEY_DATA.Equals(prop.GetKey())) {
                  // Expect subtags like "<A><B>Hello</B></A>"
                  string tmp = reader.ReadInnerXml();
                  tmp = tmp.Trim();
                  if (tmp.StartsWith("<![CDATA[")) { // strip CDATA token
                     tmp = tmp.Substring("<![CDATA[".Length);
                     if (tmp.EndsWith("]]>")) {
                        tmp = tmp.Substring(0, tmp.Length - "]]>".Length);
                     }
                  }
                  //XmlReader nestedReader = reader.ReadSubtree();
                  //tmp = reader.ReadString(); is empty if contains tags
                  //tmp = reader.Value; is empty if contains tags
                  prop.SetValue(tmp);
               }
               else {
                  prop.SetValue(reader.ReadElementContentAsString());
               }

               // Check if base64 encoded (this tag is a previous sibling before the content prop)
               if (PropTO.KEY_ENCODING.Equals(prop.GetKey())) {
                  queryEncoding = prop.GetValueRaw();
               }
               if (PropTO.KEY_RESULTENCODING.Equals(prop.GetKey())) {
                  resultEncoding = prop.GetValueRaw();
               }
               if (PropTO.KEY_DATAENCODING.Equals(prop.GetKey())) {
                  dataEncoding = prop.GetValueRaw();
               }

               list.Add(prop);
            } while (PROP.Equals(reader.LocalName));//reader.ReadToNextSibling(PROP));
         }
         return list;
      }

      /// <summary>
      /// If was a string its UTF8 byte[] representation
      /// If base64 the original byte[]
      /// </summary>
      /// <returns>never null</returns>
      public byte[] GetBlobValue() {
         if (this.value == null || this.value.Length < 1) return new byte[0];
         if (!isBase64Encoding()) return System.Text.Encoding.UTF8.GetBytes(this.value);
         try {
            return System.Convert.FromBase64String(this.value);
         }
         catch (System.FormatException) {
            System.Console.WriteLine("Base 64 string length is not " +
                "4 or is not an even multiple of 4.");
            return new byte[0];
         }
      }

      public void ReadXml(XmlReader reader) {
         reader.ReadToFollowing(PROP);
         reader.MoveToAttribute(KEY);
         this.key = reader.ReadContentAsString();
         reader.MoveToContent();
         this.value = reader.ReadElementContentAsString();
      }

      public void WriteXml(XmlWriter writer) {
         writer.WriteStartElement(PROP);
         writer.WriteStartAttribute(KEY);
         writer.WriteValue(GetKey());
         writer.WriteEndAttribute();
         writer.WriteRaw(GetValue());
         writer.WriteEndElement();
      }

      public XmlSchema GetSchema() {
         return null;
      }
   }
}
