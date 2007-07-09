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
         DateTime dt = Stuff.UtcDateTimeFromIsoString(str);
         string newStr = Stuff.ToUtcIsoDateTimeString(dt);
         Assert.AreEqual(str, newStr);
      }
      [Test]
      public void CheckEpochMillis() {
         //Stuff.ToUtcMillisecondsEpoch(DateTime dateTime)
         //Stuff.GetCurrentUtcMillisecondsEpoch()
         //Stuff.DateTimeFromUtcMillisecondsEpoch(long milliEpoch)
         
         // From Java:
         // millis=1183965018906
         // gmt=     9 Jul 2007 07:10:18
         // GMT iso= 2007-07-09 07:10:18.906Z
         ///local=   09.07.2007 09:10:18
         long utcMillis = 1183965018906L;
         DateTime dateTimeUtc = Stuff.DateTimeFromUtcMillisecondsEpoch(utcMillis);
         long newUtcMillis = Stuff.ToUtcMillisecondsEpoch(dateTimeUtc);
         string isoUtcStr= Stuff.ToUtcIsoDateTimeString(dateTimeUtc);
         Console.WriteLine("dateTimeUtc=" + isoUtcStr
            + " utcMillis=" + utcMillis + " newUtcMillis=" + newUtcMillis);
         Assert.AreEqual(utcMillis, newUtcMillis);
         // TODO: The millis are missing!!!! Assert.AreEqual("2007-07-09 07:10:18.906Z", newUtcMillis);
         Assert.AreEqual("2007-07-09 07:10:18.906Z", isoUtcStr);

         long millis = Stuff.GetCurrentUtcMillisecondsEpoch();
         Console.WriteLine("Current millis=" + millis);
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
