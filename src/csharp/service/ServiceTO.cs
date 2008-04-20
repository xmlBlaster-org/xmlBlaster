/*----------------------------------------------------------------------------
Name:      ServiceTO.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   A generic service approach
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      04/2007
See:       http://www.xmlblaster.org/
-----------------------------------------------------------------------------*/
using System;
using System.Collections.Generic;
using System.Collections;
using System.Text;
using System.Xml;
using System.Xml.Schema;
using System.Xml.Serialization;

namespace org.xmlBlaster.contrib.service {
   /// <summary>
   /// The transfer object for services
   /// </summary>
   [XmlRootAttribute("s", IsNullable = false)]
   public class ServiceTO : IXmlSerializable {

      public static readonly string SERVICE = "s"; // tag name

      private List<PropTO> propTOs;

      public ServiceTO() {
         this.propTOs = new List<PropTO>();
      }

      public ServiceTO(List<PropTO> propTOs) {
         this.propTOs = (propTOs == null) ? new List<PropTO>() : propTOs;
      }

      public List<PropTO> getProps() {
         if (this.propTOs == null)
         {
            this.propTOs = new List<PropTO>();
         }
         return this.propTOs;
      }

      public PropTO getProp(String key) {
         if (key == null)
            return null;
         if (this.propTOs == null)
            return null;
         foreach (PropTO propTO in getProps()) {
            if (key.Equals(propTO.GetKey()))
               return propTO;
         }
         return null;
      }

      /// <summary>
      /// Access the raw property data
      /// </summary>
      /// <param name="key"></param>
      /// <returns>Never null</returns>
      public byte[] getPropValueBytes(String key)
      {
         PropTO propTO = getProp(key);
         if (propTO == null)
            return new byte[0];
         byte[] val = propTO.getValueBytes();
         return (val == null) ? new byte[0] : val;
      }

      /**
       * Access the property value.
       * @param key
       * @return never null
       */
      public String getPropValue(String key) {
         PropTO propTO = getProp(key);
         if (propTO == null)
            return "";
         String val = propTO.GetValue();
         return (val == null) ? "" : val;
      }

      public String getPropValue(String key, String defaultValue)
      {
         PropTO propTO = getProp(key);
         if (propTO == null)
            return defaultValue;
         String val = propTO.GetValue();
         return (val == null) ? defaultValue : val;
      }

      public bool addProp(PropTO propTO) {
         if (propTO == null) return false;
         getProps().Add(propTO);
         return true;
      }

      public void setProps(List<PropTO> propTOs) {
         this.propTOs = propTOs;
      }

      public static List<ServiceTO> ReadSiblings(XmlReader reader) {
         List<ServiceTO> list = new List<ServiceTO>();
         /*
          * true if a matching descendant element is found; otherwise false.
          * If a matching child element is not found, the XmlReader is positioned
          * on the end tag (NodeType is XmlNodeType.EndElement) of the element.
          * If the XmlReader is not positioned on an element when ReadToDescendant
          * was called, this method returns false and the position of the XmlReader
          * is not changed. 
          */
         bool found = reader.ReadToDescendant(ServiceTO.SERVICE);
         if (found) {
            do {
               ServiceTO service = new ServiceTO(new List<PropTO>());
               service.setProps(PropTO.ReadSiblings(reader));
               list.Add(service);
            } while (reader.ReadToNextSibling(ServiceTO.SERVICE));
         }

         return list;
      }

      public void ReadXml(XmlReader reader) {
         bool found = true;
         if (!SERVICE.Equals(reader.LocalName))
            found = reader.ReadToDescendant(SERVICE);
         if (found) {
            this.propTOs = PropTO.ReadSiblings(reader);
         }
      }

      public void WriteXml(XmlWriter writer) {
         writer.WriteStartElement(SERVICE);
         foreach (PropTO propTO in getProps()) {
            propTO.WriteXml(writer);
         }
         writer.WriteEndElement();
      }

      public XmlSchema GetSchema() {
         return null;
      }

      public static ServiceTO parse(string xml) {
         ServiceTO service = org.xmlBlaster.util.Serialization.DeserializeStr<ServiceTO>(xml);
         return service;
      }

      public string ToXml() {
         string xml = org.xmlBlaster.util.Serialization.SerializeStr<ServiceTO>(this);
         return xml;
      }
   }
}
