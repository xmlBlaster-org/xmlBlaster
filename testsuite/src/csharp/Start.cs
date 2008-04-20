/*
gacutil -l | grep -i unit

gmcs /unsafe /r:nunit.framework /define:"XMLBLASTER_MONO" -debug+ -out:Start.exe *.cs service/*.cs ../../../src/csharp/XmlBlasterAccess.cs ../../../src/csharp/Key.cs ../../../src/csharp/Qos.cs ../../../src/csharp/service/*.cs  ../../../src/csharp/util/*.cs
mono Start.exe

gmcs /unsafe /r:nunit.framework /t:library /define:"XMLBLASTER_MONO" -debug+ -out:xmlBlasterClient.dll *.cs service/*.cs ../../../src/csharp/XmlBlasterAccess.cs ../../../src/csharp/Key.cs ../../../src/csharp/Qos.cs ../../../src/csharp/service/*.cs  ../../../src/csharp/util/*.cs
mono nunit-console.exe xmlBlasterClient.dll

gacutil -l
csc /unsafe /r:nunit.framework /t:library -debug+ -out:xmlBlasterClient.dll *.cs ..\..\..\src\csharp\XmlBlasterAccess.cs ..\..\..\src\csharp\Key.cs ..\..\..\src\csharp\Qos.cs ..\..\..\src\csharp\service\*.cs  ..\..\..\src\csharp\util\*.cs
*/
using System;
using System.IO;
using System.Collections;
//using System.Xml;
using org.xmlBlaster.client;
using org.xmlBlaster.util;
using org.xmlBlaster.contrib.service;

public class Start {
   static void Main(string[] argv) {
      Console.WriteLine("Startup");

      simpleServiceTest();

      /*

      testStuff();

      TestSer testSer = new TestSer("William", 54);
      //testSer.name = "Jack";
      //testSer.age = 23;

#if XMLBLASTER_MONO
      string path = @"/tmp";
#else
      string path = @"C:\tmp\in";
#endif
      string name = Path.Combine(path, "2007-04-20T12-10-44.xml");
      FileLocator.writeBinaryFile(name, Serialization.Serialize<TestSer>(testSer));

      TestSer[] arr = FileLocator.GetObjectsFromFiles<TestSer>(path, "*.xml");
      foreach (TestSer f in arr) {
         Console.WriteLine("File: -> " + f.ToString());
      }
      //testNmea();
      //testXmlBlaster();
      testService();
       */
   }

   public static void simpleServiceTest() {
      string xml = "<s>"
   + "<p k='serviceName'>track</p>"
   + "<p k='result'>&lt;A&gt;&lt;B&gt;Hallo&amp;&lt;/B&gt;&lt;C /&gt;&lt;/A&gt;</p>"
   + "</s>";
      ServiceTO service = ServiceTO.parse(xml);

      Console.WriteLine(service.getPropValue("result"));

      Console.WriteLine("Done");
   }

   public class TestSer {
      public TestSer() {
      }
      public TestSer(string n, int a) {
         this.name = n;
         this.age = a;
      }
      public string name;
      public int age;
      public override string ToString() {
         return "name=" + this.name + " age=" + this.age;
      }
   }

   private static void testStuff() {
      //Stuff.ToUtcMillisecondsEpoch(DateTime dateTime)
      //Stuff.GetCurrentUtcMillisecondsEpoch()
      //Stuff.DateTimeFromUtcMillisecondsEpoch(long milliEpoch)
      long millis = Stuff.GetCurrentUtcMillisecondsEpoch();
      Console.WriteLine("Current millis=" + millis);

      // From Java: millis=1183965018906 gmt=9 Jul 2007 07:10:18 GMT iso=2007-07-09 07:10:18.906Z local=09.07.2007 09:10:18
      long utcMillis = 1183965018906L;
      DateTime dateTimeUtc = Stuff.DateTimeFromUtcMillisecondsEpoch(utcMillis);
      long newUtcMillis = Stuff.ToUtcMillisecondsEpoch(dateTimeUtc);
      Console.WriteLine("dateTimeUtc=" + Stuff.ToUtcIsoDateTimeString(dateTimeUtc)
         + " utcMillis=" + utcMillis + " newUtcMillis=" + newUtcMillis);



      DateTime now = DateTime.Now;
      string nowUtc = Stuff.ToUtcIsoDateTimeString(now);
      Console.WriteLine("Now UTC is: " + nowUtc);
      DateTime again = Stuff.UtcDateTimeFromIsoString(nowUtc);
      Console.WriteLine("--->" + Stuff.ToUtcIsoDateTimeString(again));


      Hashtable h = new Hashtable();
      //h.Add("key1", "value1");
      h.Add("key<", "<&>!§");
      string xml = Stuff.ToClientPropertiesXml(h, true);
      Console.WriteLine(xml);
   }

   private static void testService() {
      ServiceTOTest t = new ServiceTOTest();
      t.CheckBase64();
      t.CheckToXml();
      t.CheckToXmlParsing2();
      //t.CheckXmlParsing();
      //t.CheckXmlSubtagsParsing();
      //t.CheckXmlBase64Parsing();
   }

   private static void testNmea() {
      NmeaTest n = new NmeaTest();
      n.CheckSerialInput();
   }

   private static void testXmlBlaster() {
      QosTest qosTest = new QosTest();
      qosTest.CheckConnectReturnQos();
      qosTest.CheckComplete();
      qosTest.CheckEmpty();
      try {
         qosTest.CheckInvalid();
      }
      catch (Exception) {
         // OK, expected
      }


      KeyTest keyTest = new KeyTest();
      keyTest.CheckComplete();

      XmlBlasterAccessTest xbTest = new XmlBlasterAccessTest();
      xbTest.CheckMethods();

      Console.WriteLine("Done");
   }
}