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
using org.xmlBlaster.client;
using org.xmlBlaster.util;
using org.xmlBlaster.contrib.service;

public class Start {
   static void Main(string[] argv) {
      Console.WriteLine("Startup");

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