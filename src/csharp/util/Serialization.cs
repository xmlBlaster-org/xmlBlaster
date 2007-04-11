using System;
using System.IO;
using System.Collections.Generic;
using System.Text;
using System.Xml;
using System.Xml.Serialization;

namespace org.xmlBlaster.util {
   public static class Serialization {

      /// <summary>
      /// Parse UTF-8 xml string
      /// </summary>
      /// <typeparam name="type"></typeparam>
      /// <param name="data"></param>
      /// <returns></returns>
      public static type Deserialize<type>(string data) {
         XmlSerializer xs = new XmlSerializer(typeof(type));
         MemoryStream memoryStream = new MemoryStream(StringToUTF8ByteArray(data));
         XmlTextWriter xmlTextWriter = new XmlTextWriter(memoryStream, Encoding.UTF8);
         return (type)xs.Deserialize(memoryStream);
         /*
         XmlSerializer ser = new XmlSerializer(typeof(type), "");
         return (type)ser.Deserialize(new StringReader(data));
         */
      }

      /// <summary>
      /// Returns the UTF-8 XML representation
      /// </summary>
      /// <typeparam name="type"></typeparam>
      /// <param name="data"></param>
      /// <returns></returns>
      public static string Serialize<type>(type data) {
         MemoryStream memoryStream = new MemoryStream();
         XmlSerializer xs = new XmlSerializer(data.GetType(), "");
         XmlTextWriter xmlTextWriter = new XmlTextWriter(memoryStream, Encoding.UTF8);
         xs.Serialize(xmlTextWriter, data);
         memoryStream = (MemoryStream)xmlTextWriter.BaseStream;
         string xml = UTF8ByteArrayToString(memoryStream.ToArray());
         return xml;
         /*
         StringWriter wr = new StringWriter();
         XmlSerializer ser = new XmlSerializer(data.GetType(), "");
         ser.Serialize(wr, data);
         return wr.ToString();
          */
      }

      /**
       * Convert a UTF-8 byte array to a UTF-16 string
       * @param dBytes UTF-8 multibyte string from C (zero terminated)
       * @return string to be used in C#
       */
      public static string UTF8ByteArrayToString(byte[] dBytes) {
         string str;
         str = System.Text.Encoding.UTF8.GetString(dBytes, 0, dBytes.Length);
         return str;
      }
      /*
      public static String UTF8ByteArrayToString(Byte[] characters) {
         UTF8Encoding encoding = new UTF8Encoding();
         String constructedString = encoding.GetString(characters); // not for CF 2?
         return (constructedString);
      }
      */
      public static Byte[] StringToUTF8ByteArray(String pXmlString) {
         UTF8Encoding encoding = new UTF8Encoding();
         Byte[] byteArray = encoding.GetBytes(pXmlString);
         return byteArray;
      }

   }
}
