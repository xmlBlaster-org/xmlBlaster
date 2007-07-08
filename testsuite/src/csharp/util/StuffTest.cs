/*----------------------------------------------------------------------------
Name:      Stuff.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      2007
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
using System;
using System.Text;
using NUnit.Framework;
using System.Collections;
using org.xmlBlaster.util;

namespace org.xmlBlaster.client.util {
   [TestFixture]
   public class StuffTest {
      [Test]
      public void CheckIsoDates() {
         string str = "2007-01-01 12:46:01Z";
         DateTime dt = Stuff.FromIsoDateTimeString(str);
         string newStr = Stuff.ToIsoDateTimeString(dt);
         Assert.AreEqual(str, newStr);
      }
      [Test]
      public void CheckClientProperties() {
         Hashtable h = new Hashtable();
         //h.Add("key1", "value1");
         h.Add("key<", "<&>!§");
         string xml = Stuff.ToClientPropertiesXml(h, true);
         Console.WriteLine(xml);
         Assert.AreEqual("\n <clientProperty name='key&lt;'>&lt;&amp;&gt;!§</clientProperty>", xml);
      }
   }
}
