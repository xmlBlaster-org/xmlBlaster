using System;
//using org.xmlBlaster.client.activex;

namespace demo
{
   /// <summary>
   ///   Example how to access xmlBlaster using the ActiveX Bridge
   ///   to access the Java client library (Windows only)
   /// </summary>
   /// <remarks>
   ///   First you need to create and register the ActiveX Bridge
   ///   with the 'build' command below.
   ///   See: xmlBlaster\src\java\org\xmlBlaster\client\activex
   ///   See: build -DJRE_HOME=C:\PROGRA~1\Java\j2re1.5.0 -DJVM.target=1.4 activex
   /// </remarks>
   class HelloWorld3
   {
      internal HelloWorld3() {}

      // TODO: Callback events never arrive
      public string update(object ev) 
      {
         Console.WriteLine("SUUUCEESS: Got update from xmlBlaster: " + ev.ToString());
         return "<qos><state id='OK'/></qos>";
      }

      // TODO: Callback events never arrive
      public string XmlScriptAccessSource_updateEventHandler(object ev) 
      {
         Console.WriteLine("SUUUCEESSSSS: Got update from xmlBlaster: " + ev.ToString());
         return "<qos><state id='OK'/></qos>";
      }

      internal void runTest() 
      {
         // See registry: 
         //   regedit -> HKEY_CLASSES_ROOT
         //           -> CLSID
         //	         -> D824B185-AE3C-11D6-ABF5-00B0D07B8581
         //           -> XmlScriptAccess Bean Control
         // org.xmlBlaster.client.activex.XmlScriptAccess

         XmlScriptAccess.XmlScriptAccessClass xmlBlaster;
         xmlBlaster = new XmlScriptAccess.XmlScriptAccessClass();
         string[] argArr = { "-protocol", "SOCKET"/*,
                             "-trace",    "true"*/ };
         xmlBlaster.initArgs(argArr);
         //Set obj = xmlBlaster.createPropertiesInstance();
         //string request = "<xmlBlaster><connect/></xmlBlaster>";

         
         XmlScriptAccess.XmlScriptAccessSource_updateEventHandler myClass1 = new XmlScriptAccess.XmlScriptAccessSource_updateEventHandler(this.update);
         // Get the type referenced by the specified type handle.
         Type myClass1Type = Type.GetTypeFromHandle(Type.GetTypeHandle(myClass1));
         Console.WriteLine("The Names of the Attributes :"+myClass1Type.Attributes);
                  
         //System.Type type = XmlScriptAccess.XmlScriptAccessSource_updateEventHandler();
         //System.Type type = System.Type.GetType("XmlScriptAccessSource_updateEventHandler"); //UpdateListener");
         XmlScriptAccess.XmlScriptAccessSource_updateEventHandler.CreateDelegate(
            myClass1Type, this, "update");
   
         {
            string request =
               "<xmlBlaster>" +
               "   <connect/>" +
               "   <wait delay='1000' />" +
               "   <publish>" +
               "      <key oid='test'><airport name='london' /></key>" +
               "      <content>This is a simple script test</content>" +
               "      <qos/>" +
               "   </publish>" +
               "</xmlBlaster>";
            string response = xmlBlaster.sendRequest(request);
            Console.WriteLine("Got response from xmlBlaster: " + response);
         }
         {
            string request =
               "<xmlBlaster>" +
               "   <subscribe><key oid='another.topix'></key><qos/></subscribe>" +
               "   <publish>" +
               "      <key oid='another.topix'/>" +
               "      <content>This is a simple script test</content>" +
               "      <qos/>" +
               "   </publish>" +
               "   <wait delay='1000'/>" +
               "</xmlBlaster>";
            string response = xmlBlaster.sendRequest(request);
            Console.WriteLine("Got response from xmlBlaster: " + response);
         }
         {
            string request =
               "<xmlBlaster>" +
               "   <disconnect/>" +
               "</xmlBlaster>";
            string response = xmlBlaster.sendRequest(request);
            Console.WriteLine("Got response from xmlBlaster: " + response);
         }
      }
      

      [STAThread]
      static void Main(string[] args) {
         demo.HelloWorld3 hw = new demo.HelloWorld3();
         hw.runTest();
      }
   }
}

