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

      public static readonly string KEY_SERVICENAME = "serviceName";

      public static readonly string KEY_QUERYTYPE = "queryType";

      public static readonly string KEY_ENCODING = "encoding";

      public static readonly string KEY_QUERY = "query";

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

      public static readonly string VALUE_RESULTENCODING_BASE64 = "base64";

      public static readonly string VALUE_RESULTENCODING_DEFAULT = VALUE_RESULTENCODING_BASE64;

      public static readonly string VALUE_RESULTMIME_PREFIX = "application/xmlBlaster.service";

      public static readonly string PROP = "prop"; // tag name

      public static readonly string KEY = "key"; // attribute name

      public static readonly string ENCODING_BASE64 = "base64";

      protected string key;

      protected string value = "";

      // transient
      protected string encoding;

      public PropTO() {
      }

      public PropTO(string key, string value) {
         this.key = key;
         this.value = value;
      }

      public string GetKey() {
         return (this.key == null) ? "" : this.key;
      }

      public void SetKey(string key) {
         this.key = key;
      }

      public string GetRawValue() {
         return this.value;
      }

      /// <summary>
      /// If it was base64 encoded it is converted as UTF-8 string
      /// </summary>
      /// <returns>Never null</returns>
      public string GetValue() {
         if (this.value == null || this.value.Length < 1) return "";
         if (!isBase64()) return this.value;

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

      public void SetValue(string value) {
         this.value = value;
      }

      public bool isBase64() {
         if (this.encoding == null) return false;
         return (VALUE_RESULTENCODING_BASE64.Equals(this.encoding));
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
            bool queryIsBase64 = false;
            bool resultIsBase64 = false;
            do {
               PropTO prop = new PropTO();
               reader.MoveToAttribute(KEY);
               prop.SetKey(reader.ReadContentAsString());
               reader.MoveToContent();
               /*This method reads the start tag, the contents of the element, and moves the reader past 
                * the end element tag. It expands entities and ignores processing instructions and comments.
                * The element can only contain simple content. That is, it cannot have child elements.
                */

               if (resultIsBase64 && PropTO.KEY_RESULT.Equals(prop.GetKey()) ||
                  queryIsBase64 && PropTO.KEY_QUERY.Equals(prop.GetKey())) {
                  prop.SetEncoding(VALUE_RESULTENCODING_BASE64);
               }

               prop.SetValue(reader.ReadElementContentAsString());

               // Check if base64 encoded (this tag is a previous sibling before the content prop)
               if (PropTO.KEY_ENCODING.Equals(prop.GetKey())) {
                  queryIsBase64 = prop.getBoolValue();
               }
               if (PropTO.KEY_RESULTENCODING.Equals(prop.GetKey())) {
                  resultIsBase64 = prop.getBoolValue();
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
         if (!isBase64()) return System.Text.Encoding.UTF8.GetBytes(this.value);
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
