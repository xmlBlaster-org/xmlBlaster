/*----------------------------------------------------------------------------
Name:      ServiceListTO.cs
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
   /// <summary>
   /// <services>
   ///   <service>
   ///    <prop key='serviceName'>buddy</prop>
   ///    <prop key='bounce'>myRequestId-5301785</prop>
   ///    <prop key='taskType'>named</prop>
   ///    <prop key='task'>getBuddyList</prop>
   ///   </service>
   ///  </services>
   /// </summary>
   [XmlRootAttribute("services", IsNullable = false)]
   public class ServiceListTO : IXmlSerializable {
      private List<ServiceTO> serviceTOs;
      public static readonly string SERVICES = "services"; // tag name

      public ServiceListTO() {
      }

      public ServiceListTO(ServiceTO serviceTO) {
         addService(serviceTO);
      }

      public void addService(ServiceTO serviceTO) {
         if (this.serviceTOs == null)
            this.serviceTOs = new List<ServiceTO>();
         this.serviceTOs.Add(serviceTO);
      }

      public List<ServiceTO> getServices() {
         return (this.serviceTOs == null) ? new List<ServiceTO>()
               : this.serviceTOs;
      }

      public void setServices(List<ServiceTO> serviceTOs) {
         this.serviceTOs = serviceTOs;
      }

      public void ReadXml(XmlReader reader) {
         //reader.ReadToFollowing(SERVICES);
         this.serviceTOs = ServiceTO.ReadSiblings(reader);
      }

      public void WriteXml(XmlWriter writer) {
         //writer.WriteStartElement(SERVICES);
         foreach (ServiceTO service in getServices()) {
            service.WriteXml(writer);
         }
         //writer.WriteEndElement();
      }

      public XmlSchema GetSchema() {
         return null;
      }

      /// <summary>
      /// </summary>
      /// <param name="prop">Of type PropTO.KEY_DATA or PropTO.KEY_RESULT</param>
      /// <returns></returns>
      public static ServiceListTO parse(byte[] bytes) {
         return org.xmlBlaster.util.Serialization.Deserialize<ServiceListTO>(bytes);
      }

      public static ServiceListTO parseStr(string xml) {
         return org.xmlBlaster.util.Serialization.DeserializeStr<ServiceListTO>(xml);
      }

      public byte[] ToXml() {
         return org.xmlBlaster.util.Serialization.Serialize<ServiceListTO>(this);
      }

      public string ToXmlStr() {
         return org.xmlBlaster.util.Serialization.SerializeStr<ServiceListTO>(this);
      }
   }
}
