/*
@file     Hello.cs
@comment  Access xmlBlaster from C# (Csharp)
@author   mr@marcelruff.info
@prepare  cd ~/xmlBlaster; build c-lib; cd ~/xmlBlaster/src/csharp; ln -s ../../lib/libxmlBlasterClientCD.so .
@compile  mcs -debug+ -out:Hello.exe NativeC.cs Hello.cs
@run      mono Hello.exe
@run      mono Hello.exe --help
@see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
@c        http://www.xmlBlaster/org
*/
using System;
using System.Runtime.InteropServices;
using org.xmlBlaster;

public class Hello
{
   static void Main(string[] argv) {
      I_XmlBlasterAccess nc = XmlBlasterAccessFactory.createInstance(argv);

      const string callbackSessionId = "secretCb";
      string connectQos = String.Format(
         "<qos>"+
         " <securityService type='htpasswd' version='1.0'>"+
         "  <![CDATA["+
         "   <user>fritz</user>"+
         "   <passwd>secret</passwd>"+
         "  ]]>"+
         " </securityService>"+
         " <queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>"+
         "   <callback type='SOCKET' sessionId='{0}'>"+
         "   </callback>"+
         " </queue>"+
         "</qos>", callbackSessionId);  //"    socket://{1}:{2}"+
      Console.WriteLine(connectQos);

      nc.connect(connectQos);
      for (int i=0; i<5; i++) {
         nc.subscribe("<key oid='Hello'/>", "<qos/>");
         nc.publish("<key oid='C#C#C#'/>", "HIIIHAAAA", "<qos/>");
         nc.publish("<key oid='C#C#C#'/>", "HIIIHOOO", "<qos/>");
         MsgUnit[] msgs = nc.get("<key oid='C#C#C#'/>", "<qos><history numEntries='6'/></qos>");
         Console.WriteLine("get() returned " + msgs.Length + " messages");
         nc.ping("<qos/>");
         nc.isConnected();
         nc.unSubscribe("<key oid='Hello'/>", "<qos/>");
         nc.erase("<key oid='Hello'/>", "<qos/>");
         Console.WriteLine("Hit a key " + i);
         Console.ReadLine();
      }
      nc.disconnect("<qos/>");
      Console.Out.WriteLine("DONE");
   }
}
