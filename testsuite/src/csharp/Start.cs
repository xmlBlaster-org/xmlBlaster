/*
gacutil -l | grep -i unit

gmcs /unsafe /r:nunit.framework /define:"XMLBLASTER_MONO" -debug+ -out:Start.exe *.cs ../../../src/csharp/XmlBlasterAccess.cs ../../../src/csharp/Key.cs ../../../src/csharp/Qos.cs
mono Start.exe

gmcs /unsafe /r:nunit.framework /t:library /define:"XMLBLASTER_MONO" -debug+ -out:xmlBlasterClient.dll *.cs ../../../src/csharp/XmlBlasterAccess.cs ../../../src/csharp/Key.cs ../../../src/csharp/Qos.cs
mono nunit-console.exe xmlBlasterClient.dll

gacutil -l
csc /unsafe /r:nunit.framework /t:library -debug+ -out:xmlBlasterClient.dll *.cs ..\..\..\src\csharp\XmlBlasterAccess.cs ..\..\..\src\csharp\Key.cs ..\..\..\src\csharp\Qos.cs
*/
using System;
using org.xmlBlaster.client;
using org.xmlBlaster.contrib.service;

public class Start
{
   static void Main(string[] argv)
   {
      Console.WriteLine("Startup");
      //testNmea();
      //testXmlBlaster();
      testService();
   }

   private static void testService() {
      ServiceTOTest t = new ServiceTOTest();
      t.CheckToXml();
      t.CheckToXmlParsing2();
      //t.CheckXmlParsing();
      //t.CheckXmlSubtagsParsing();
      //t.CheckXmlBase64Parsing();
   }

   private static void testNmea()
   {
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