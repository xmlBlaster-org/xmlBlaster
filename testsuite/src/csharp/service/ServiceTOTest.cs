/*----------------------------------------------------------------------------
Name:      ServiceTOTest.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test cases for service XML markup
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      04/2007
See:       http://www.xmlblaster.org/
-----------------------------------------------------------------------------*/
using System;
using System.Collections.Generic;
using System.Text;
using NUnit.Framework;
using System.Reflection;
using System.Collections;

namespace org.xmlBlaster.contrib.service {
   [TestFixture]
   public class ServiceTOTest {

      public ServiceTOTest() {
      }

      [SetUp]
      public void Init() {
      }

      [TearDown]
      public void Dispose() { /* ... */ }

      /*
       * using (XmlReader reader = XmlReader.Create("2books.xml")) {

  // Move the reader to the second book node.
  reader.MoveToContent(); 
  reader.ReadToDescendant("book");
  reader.Skip(); //Skip the first book.

  // Parse the file starting with the second book node.
  do {
     switch (reader.NodeType) {
        case XmlNodeType.Element:
           Console.Write("<{0}", reader.Name);
           while (reader.MoveToNextAttribute()) {
               Console.Write(" {0}='{1}'", reader.Name, reader.Value);
           }
           Console.Write(">");
           break;
        case XmlNodeType.Text:
           Console.Write(reader.Value);
           break;
        case XmlNodeType.EndElement:
           Console.Write("</{0}>", reader.Name);
           break;
     }       
  }  while (reader.Read());    

}

*/

      [Test]
      public void CheckToXml() {
         ServiceListTO serviceList = new ServiceListTO();
         {
            ServiceTO service = new ServiceTO();
            serviceList.addService(service);
            service.addProp(new PropTO(PropTO.KEY_SERVICENAME, "buddy"));
         }
         byte[] bytes = serviceList.ToXml();
         string xml = serviceList.ToXmlStr();
         Console.WriteLine("CheckToXml: " + xml);

         {
            ServiceListTO serviceList2 = ServiceListTO.parseStr(xml);
            Console.WriteLine(serviceList2.ToXmlStr());
            Assert.AreEqual(1, serviceList2.getServices().Count);
            foreach (ServiceTO service in serviceList2.getServices()) {
               Assert.AreEqual("buddy", service.getPropValue(PropTO.KEY_SERVICENAME));
            }
         }
         {
            ServiceListTO serviceList2 = ServiceListTO.parse(bytes);
            Console.WriteLine(serviceList2.ToXmlStr());
            Assert.AreEqual(1, serviceList2.getServices().Count);
            foreach (ServiceTO service in serviceList2.getServices()) {
               Assert.AreEqual("buddy", service.getPropValue(PropTO.KEY_SERVICENAME));
            }
         }
         Console.WriteLine("CheckToXml");
      }

            [Test]
      public void CheckBase64() {
         byte[] bytes = org.xmlBlaster.util.Serialization.StringToUTF8ByteArray("Hello");
         PropTO prop = new PropTO(PropTO.KEY_DATA, bytes);
         Assert.AreEqual("SGVsbG8=", prop.GetValueRaw());
         Assert.AreEqual("Hello", prop.GetValue());
      }


      [Test]
      public void CheckToXmlParsing2() {
         string xmlService =
         "<services>" +
           "<service>" +
             "<prop key=\"serviceName\">buddy</prop>" +
             "<prop key=\"task\">getBuddyList</prop>" +
             "<prop key=\"resultMime\">application/watchee.service.buddy.buddylist</prop>" +
             "<prop key=\"resultEncoding\">base64</prop>" +
             "<prop key=\"result\">PGJ1ZGR5bGlzdCBsb2dpbk5hbWU9ImpvZUBteWNvbXAuaW5mbyIgdHlwZT0iYWxsIj4KICA8YnVkZHk+CiAgICA8bG9naW5OYW1lPmphY2tAc29tZS5vcmc8L2xvZ2luTmFtZT4KICAgIDxhbGlhcz5qYWNrPC9hbGlhcz4KICAgIDxwZXJtaXNzaW9uIG5hbWU9ImdwcyIgZGVzY3JpcHRpb249IlNob3cgbXkgcG9zaXRpb24iLz4KICAgIDxwZXJtaXNzaW9uIG5hbWU9InhzbXMiIGRlc2NyaXB0aW9uPSJTZW5kL1JlY2VpdmUgbWFpbHMiLz4KICA8L2J1ZGR5Pgo8L2J1ZGR5bGlzdD4=</prop>" +
           "</service>" +
         "</services>";
         ServiceListTO serviceList = ServiceListTO.parseStr(xmlService);
         Assert.AreEqual(1, serviceList.getServices().Count);
         foreach (ServiceTO service in serviceList.getServices()) {
            Assert.AreEqual(5, service.getProps().Count);
            string resultMime = service.getPropValue(PropTO.KEY_RESULTMIME);
            string xml = service.getPropValue(PropTO.KEY_RESULT);
            Console.WriteLine("Handling resultMime=" + resultMime + " xml=" + xml);
         }
      }

      [Test]
      public void CheckXmlParsing() {
         {
            string xml = "<?xml version='1.0' encoding='utf-8'?>\r\n<service>"
               + "<prop key='serviceName'>track</prop>"
               + "<prop key='taskType'>named</prop>"
               + "<prop key='task'>myStandardTrackIdQuery('Summer')</prop>"
               + "<prop key='resultMime'>application/service-buddy</prop>"
               + "</service>";
            ServiceTO service = ServiceTO.parse(xml);
            Assert.IsNotNull(service);
            Assert.AreEqual(4, service.getProps().Count);
            Assert.AreEqual("track", service.getPropValue(PropTO.KEY_SERVICENAME));
            Assert.AreEqual("named", service.getPropValue(PropTO.KEY_TASKTYPE));
            Assert.AreEqual("myStandardTrackIdQuery('Summer')", service.getPropValue(PropTO.KEY_TASK));
            Assert.AreEqual("application/service-buddy", service.getPropValue(PropTO.KEY_RESULTMIME));
         }

         {
            string xml = "<?xml version='1.0' encoding='utf-8'?>\r\n"
               + "<services><service>"
               + "<prop key='serviceName'>track</prop>"
               + "<prop key='taskType'>named</prop>"
               + "<prop key='task'>myStandardTrackIdQuery('Summer')</prop>"
               + "<prop key='resultMime'>application/watchee-service-buddy-</prop>"
               + "</service></services>";
            ServiceListTO serviceList = ServiceListTO.parseStr(xml);
            Assert.IsNotNull(serviceList);
            Assert.AreEqual(1, 1);
         }
         Console.WriteLine("CheckXmlParsing");
      }

      [Test]
      public void CheckXmlSubtagsParsing() {
         {
            string xml = "<service>"
               + "<prop key='serviceName'>track</prop>"
               + "<prop key='result'><A><B>Hallo</B><C /></A></prop>"
               + "</service>";
            ServiceTO service = ServiceTO.parse(xml);
            Assert.IsNotNull(service);
            Assert.AreEqual(2, service.getProps().Count);
            Assert.AreEqual("<A><B>Hallo</B><C /></A>", service.getPropValue(PropTO.KEY_RESULT));
         }

         {
            string xml = "<service>"
               + "<prop key='serviceName'>track</prop>"
               + "<prop key='result'><![CDATA[<A><B>Hallo</B><C /></A>]]></prop>"
               + "</service>";
            ServiceTO service = ServiceTO.parse(xml);
            Assert.IsNotNull(service);
            Assert.AreEqual(2, service.getProps().Count);
            Assert.AreEqual("<A><B>Hallo</B><C /></A>", service.getPropValue(PropTO.KEY_RESULT));
         }

         Console.WriteLine("CheckXmlSubtagsParsing");
      }

      [Test]
      public void CheckXmlBase64Parsing() {
         {
            string xml = "<service>"
               + "<prop key='serviceName'>track</prop>"
               + "<prop key='resultEncoding'>base64</prop>"
               + "<prop key='result'>QmxhPEJsYUJsYQ==</prop>"
               + "</service>";
            ServiceTO service = ServiceTO.parse(xml);
            Assert.IsNotNull(service);
            Assert.AreEqual(3, service.getProps().Count);
            string tmp = service.getPropValue(PropTO.KEY_RESULT);
            Assert.AreEqual("Bla<BlaBla", service.getPropValue(PropTO.KEY_RESULT));
         }

         Console.WriteLine("CheckXmlBase64Parsing");
      }
   }
}
