using System;
using org.xmlBlaster.client.activex;

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
   /// <author>
   ///   Marcel Ruff
   /// </author>
   class XmlBlasterCSharp
   {
      [STAThread]
      static void Main(string[] args)
      {
         // See registry: 
         //   regedit -> HKEY_CLASSES_ROOT
         //           -> CLSID
         //           -> D824B185-AE3C-11D6-ABF5-00B0D07B8581
         //           -> XmlScriptAccess Bean Control
         // org.xmlBlaster.client.activex.XmlScriptAccess

         XmlScriptAccess.XmlScriptAccessClass xmlBlaster;
         xmlBlaster = new XmlScriptAccess.XmlScriptAccessClass();

         string[] argArr = { "-protocol", "SOCKET" };
         xmlBlaster.initArgs(argArr);

         string request = "<xmlBlaster>" +
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
   }
}

