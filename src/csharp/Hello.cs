/*
@file     Hello.cs
@comment  Access xmlBlaster from C# (Csharp)
@author   mr@marcelruff.info

@prepare  Linux:    cd ~/xmlBlaster; build c-lib; cd ~/xmlBlaster/src/csharp; ln -s ../../lib/libxmlBlasterClientCD.so .
@compile  Linux:    mcs /unsafe /d:"XMLBLASTER_MONO" -debug+ -out:Hello.exe NativeC.cs XmlBlasterAccess.cs Hello.cs

@prepare  Windows:  Compile the C client library first (see xmlBlaster\src\c\xmlBlasterClientC.sln)
@compile  Windows:  csc /unsafe -debug+ -out:Hello.exe XmlBlasterAccess.cs PInvokeCE.cs Hello.cs

@prepare  WindowsCE:  Compile the C client library first (see xmlBlaster\src\c\WindowsCE\xmlBlasterCE.sln)
                      or download the dll binaries xmlBlasterClientC-Arm4.dll, pthreads270-Arm4.dll, zlib123-Arm4.dll
@compile  WindowsCE:  It is best to compile from VC++ 8.0, the compile looks something like
                      C:\WINDOWS\Microsoft.NET\Framework\v1.1.4322\Csc.exe /noconfig /nostdlib+ -debug+ /unsafe /define:Smartphone /reference:..\..\lib\xmlBlasterClientCD-Arm4.dll -out:Hello.exe PInvokeCE.cs Hello.cs XmlBlasterAccess.cs
                      

@run      mono Hello.exe
@run      mono Hello.exe --help
@see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
@c        http://www.xmlBlaster/org
*/
using System;
using System.Runtime.InteropServices;
using org.xmlBlaster.client;

public class Hello : I_Callback
{
   const string callbackSessionId = "secretCb";
   
   static void Main(string[] argv) {
      new Hello(argv);
   }
   
   public Hello(string[] argv) {
      I_XmlBlasterAccess nc = XmlBlasterAccessFactory.createInstance(argv);

      string connectQos = String.Format(
         "<qos>\n"+
         " <securityService type='htpasswd' version='1.0'>\n"+
         "  <![CDATA[\n"+
         "   <user>fritz</user>\n"+
         "   <passwd>secret</passwd>\n"+
         "  ]]>\n"+
         " </securityService>\n"+
         " <queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>\n"+
         "   <callback type='SOCKET' sessionId='{0}'>\n"+
         "   </callback>\n"+
         " </queue>\n"+
         "</qos>", callbackSessionId);  //"    socket://{1}:{2}"+
      Console.WriteLine("Connecting with:" + connectQos);

      I_Callback callback = this;
      nc.connect(connectQos, callback);

      Console.WriteLine("Connected.");

      for (int i=0; i<5; i++) {
         string srq = nc.subscribe("<key oid='Hello'/>", "<qos/>");
         Console.WriteLine("subscribe() returned " + srq);
         
         //nc.publish("<key oid='Hello'/>", "HIII", "<qos/>");
         string prq = nc.publish("<key oid='C#C#C#'/>", "HIIIHAAAA", "<qos/>");
         Console.WriteLine("publish() returned " + prq);         
         
         nc.publish("<key oid='C#C#C#'/>", "HIIIHOOO", "<qos/>");
         Console.WriteLine("publish() returned " + prq);         
         
         MsgUnit[] msgs = nc.get("<key oid='C#C#C#'/>", "<qos><history numEntries='6'/></qos>");
         Console.WriteLine("get() returned " + msgs.Length + " messages");         
         
         string p = nc.ping("<qos/>");
         Console.WriteLine("ping() returned " + p);         
         
         bool b = nc.isConnected();
         Console.WriteLine("isConnected() returned " + b);         
         
         string[] urq = nc.unSubscribe("<key oid='Hello'/>", "<qos/>");
         Console.WriteLine("unSubscribe() returned " + urq[0]);         
         
         string[] erq = nc.erase("<key oid='C#C#C#'/>", "<qos/>");
         Console.WriteLine("erase() returned " + erq[0]);         
         
         Console.WriteLine("\nHit a key " + i);
         Console.ReadLine();
      }
      bool drq = nc.disconnect("<qos/>");
      Console.WriteLine("disconnect() returned " + drq);
      
      Console.WriteLine("DONE");
   }
   
   #region I_Callback Members
   public string OnUpdate(string cbSessionId, MsgUnit msgUnit) {
      Console.WriteLine("OnUpdate() invoked START ==================");
      if (callbackSessionId != cbSessionId)
         Console.WriteLine("Not authorized");
      Console.WriteLine(msgUnit.getKey());
      Console.WriteLine(msgUnit.getContentStr());
      Console.WriteLine(msgUnit.getQos());
      string ret = "<qos><state id='OK'/></qos>";
      Console.WriteLine("OnUpdate() invoked DONE ===================");
      //throw new XmlBlasterException("user.update.illegalArgument", "A test exception from OnUpdate()");
      return ret;
   }
   #endregion
}

