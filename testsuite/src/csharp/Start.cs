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
using System.Threading;
//using System.Xml;
using org.xmlBlaster.client;
using org.xmlBlaster.util;
using org.xmlBlaster.contrib.service;

public class Start : I_LoggingCallback, I_ConnectionStateListener {
   public void OnLogging(XmlBlasterLogLevel logLevel, string location, string message)
   {
      Console.WriteLine(logLevel + " " + location + " " + message);
   }

   static void Main(string[] argv) {
      Console.WriteLine("Startup");

      //new Start().testXmlBlasterPing();

      new Start().testXmlBlaster();

      //(new Start()).TestPinger();
      
      //simpleServiceTest();

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

   private void TestPinger()
   {
      XmlBlasterAccess xbAccess = new XmlBlasterAccess();
      long sleepMillis = 5000;
      XbPinger xbPinger = new XbPinger(xbAccess, sleepMillis, this);
      xbPinger.Start();

      Thread.Sleep(12*1000);
      xbPinger.Stop();

      Thread.Sleep(10000000);
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

   private void testXmlBlasterPing()
   {
      try
      {
         I_XmlBlasterAccess xb = XmlBlasterAccessFactory.CreateInstance();
         xb.RegisterConnectionListener(this);
         Hashtable properties = new Hashtable();
         properties.Add("dispatch/connection/pingInterval", "12500");
         properties.Add("dispatch/connection/delay", "8600");
         properties.Add("dispatch/connection/pollOnInitialConnectFail", "true");
         xb.Initialize(properties);
         string connectQos =
            "<qos>\n" +
            " <securityService type='htpasswd' version='1.0'>\n" +
            "   <user>fritz</user>\n" +
            "   <passwd>secret</passwd>\n" +
            " </securityService>\n" +
            "</qos>";
         ConnectReturnQos crq = xb.Connect(connectQos, null);
         Console.WriteLine("Done, sleeping now");
      }
      catch (Exception e)
      {
         Console.WriteLine("Test failed, sleeping now: " + e.ToString());
      }

      Thread.Sleep(1000000);
   }

   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection)
   {
      Console.WriteLine("****CLIENT reachedAlive " + oldState + "->ALIVE");
   }

   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection)
   {
      Console.WriteLine("****CLIENT reachedPolling " + oldState + "->POLLING");
   }

   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection)
   {
      Console.WriteLine("****CLIENT reachedDead " + oldState + "->DEAD");
   }


   private void testXmlBlaster() {
      QosTest qosTest = new QosTest();
      qosTest.CheckConnectReturnQos();
      qosTest.CheckComplete();
      qosTest.CheckEmpty();
      try {
         qosTest.CheckInvalid();
      }
      catch (Exception e) {
         Console.WriteLine("OK Expected: " + e.ToString());
      }


      KeyTest keyTest = new KeyTest();
      keyTest.CheckComplete();

      XmlBlasterAccessTest xbTest = new XmlBlasterAccessTest();
      xbTest.CheckMethods();

      Console.WriteLine("Done");
   }
}