using System;
using System.IO;
using System.Collections.Generic;
using System.Text;
using System.Xml;
using System.Xml.Serialization;

namespace org.xmlBlaster.util {
   public static class Serialization {

      public static type Deserialize<type>(byte[] data) {
         XmlSerializer xs = new XmlSerializer(typeof(type));
         MemoryStream memoryStream = new MemoryStream(data);
         /*
         XmlWriterSettings settings = new XmlWriterSettings();
         //settings.OmitXmlDeclaration = true;
         //settings.ConformanceLevel = ConformanceLevel.Fragment;
         //settings.CloseOutput = false;
         //settings.OmitXmlDeclaration = true;
         settings.Encoding = Encoding.UTF8;
         XmlTextWriter xmlTextWriter = XmlWriter.Create(memoryStream, settings);
         //XmlTextWriter xmlTextWriter = new XmlTextWriter(memoryStream, Encoding.UTF8);
         */
         return (type)xs.Deserialize(memoryStream);
      }

      /// <summary>
      /// Parse UTF-8 xml string
      /// </summary>
      /// <typeparam name="type"></typeparam>
      /// <param name="data"></param>
      /// <returns></returns>
      public static type DeserializeStr<type>(string data) {
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
      public static byte[] Serialize<type>(type data) {
         MemoryStream memoryStream = new MemoryStream();
         XmlSerializer xs = new XmlSerializer(data.GetType());
         XmlWriterSettings settings = new XmlWriterSettings();
         //settings.OmitXmlDeclaration = true;
         //settings.ConformanceLevel = ConformanceLevel.Fragment;
         //settings.CloseOutput = false;
         //settings.OmitXmlDeclaration = true;
         settings.Encoding = Encoding.UTF8;
         XmlWriter xmlWriter = XmlWriter.Create(memoryStream, settings);
         xs.Serialize(xmlWriter, data);
         //memoryStream = (MemoryStream)xmlTextWriter.BaseStream;
         return memoryStream.ToArray();
      }

      public static string SerializeStr<type>(type data) {
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
