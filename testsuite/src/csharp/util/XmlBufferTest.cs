/*----------------------------------------------------------------------------
Name:      XmlBufferTest.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      2008
See:       http://www.xmlblaster.org
-----------------------------------------------------------------------------*/
using System;
using System.Text;
using NUnit.Framework;
using System.Collections;
using org.xmlBlaster.util;

namespace org.xmlBlaster.util {
   [TestFixture]
   public class XmlBufferTest {
      [Test]
      public void CheckEscape() {
         XmlBuffer b = new XmlBuffer(1020);
         b.Append("<pc><pr key='").AppendAttributeEscaped("Oh&<").Append("'>").AppendEscaped("Oi<>!").Append("</pr></pc>");
         string xml = b.ToString();
         Assert.AreEqual("<pc><pr key='Oh&amp;&lt;'>Oi&lt;&gt;!</pr></pc>", xml);
      }
      [Test]
      public void CheckUnEscape() {
         string xml = "&lt;pc>&lt;pr k=&apos;aKey' d='0'/></pc&gt;";
         string res = XmlBuffer.UnEscape(xml);
         Assert.AreEqual("<pc><pr k='aKey' d='0'/></pc>", res);
      }
   }
}
