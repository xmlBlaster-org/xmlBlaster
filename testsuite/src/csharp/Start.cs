using System;
using org.xmlBlaster.client;
public class Start
{
   static void Main(string[] argv)
   {
      Console.WriteLine("Startup");
      /*
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
      */

      KeyTest keyTest = new KeyTest();
      keyTest.CheckComplete();

      //XmlBlasterAccessTest xbTest = new XmlBlasterAccessTest();
      //xbTest.CheckMethods();

      Console.WriteLine("Done");
   }
}