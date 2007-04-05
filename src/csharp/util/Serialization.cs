using System;
using System.IO;
using System.Collections.Generic;
using System.Text;
using System.Xml.Serialization;

namespace org.xmlBlaster.client.util {
   public static class Serialization {
      public static type Deserialize<type>(string data) {
         XmlSerializer ser = new XmlSerializer(typeof(type), "");
         return (type)ser.Deserialize(new StringReader(data));
      }

      public static string Serialize<type>(type data) {
         StringWriter wr = new StringWriter();
         XmlSerializer ser = new XmlSerializer(data.GetType(), "");
         ser.Serialize(wr, data);
         return wr.ToString();
      }
   }
}
