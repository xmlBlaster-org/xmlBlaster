/*
@file     TestPInvoke.cs
@comment  Access xmlBlaster from C# (Csharp)
@author   mr@marcelruff.info
@compile  csc /unsafe -debug+ /define:FORCE_PINVOKECE -out:TestPInvoke.exe PInvokeCE.cs XmlBlasterAccess.cs TestPInvoke.cs
@see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
*/
using System;
using System.Runtime.InteropServices;
using org.xmlBlaster;

public class TestPInvoke : I_Callback
{
   const string callbackSessionId = "secretCb";
   
   static void Main(string[] argv) {
       Console.WriteLine("MAIN ENTERED");
       new TestPInvoke(argv);
   }
   
   public TestPInvoke(string[] argv) {
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

      //for (int count=0; count<10; count++) {
      
      string srq = nc.subscribe("<key oid='TestPInvoke'/>", "<qos/>");
      Console.WriteLine("TestPInvoke.cs: subscribe() returned " + srq);

      srq = nc.subscribe("<key oid='TestPInvoke'/>", "<qos/>");
      Console.WriteLine("TestPInvoke.cs: subscribe() returned " + srq);

      string[] urq = nc.unSubscribe("<key oid='TestPInvoke'/>", "<qos/>");
      Console.WriteLine("unSubscribe() returned");
      for (int i = 0; i < urq.Length; i++)
         Console.WriteLine("unSubscribeReturn #{0}:\n{1}", i, urq[i]);
      GC.Collect();
      GC.Collect();
      
      string prq = nc.publish("<key oid='C#C#C#'/>", "HIIIHAAAA", "<qos/>");
      Console.WriteLine("publish() returned " + prq);

      prq = nc.publish("<key oid='C#C#'/>", "HIIIHAAAA", "<qos/>");
      Console.WriteLine("publish() returned " + prq);

      prq = nc.publish("<key oid='C#'/>", "HIIIHAAAA", "<qos/>");
      Console.WriteLine("publish() returned " + prq);
      
      MsgUnit[] msgs = nc.get("<key oid='C#C#C#'/>", "<qos><history numEntries='6'/></qos>");
      Console.WriteLine("get(C#C#C#) returned " + msgs.Length + " messages");
      for (int i = 0; i < msgs.Length; i++)
         Console.WriteLine(msgs[i].ToString());

      msgs = nc.get("<key oid='unknown'/>", "<qos><history numEntries='6'/></qos>");
      Console.WriteLine("get(unknown) returned " + msgs.Length + " messages");

      string[] erq = nc.erase("<key queryType='XPATH'>//key</key>", "<qos/>");
      Console.WriteLine("erase() returned");
      for (int i = 0; i < erq.Length; i++)
         Console.WriteLine("eraseReturn #{0}:\n{1}", i, erq[i]);

      string p = nc.ping("<qos/>");
      Console.WriteLine("ping() returned " + p);

      bool b = nc.isConnected();
      Console.WriteLine("isConnected() returned " + b);
   //}
      /*
      for (int i=0; i<5; i++) {
         string srq = nc.subscribe("<key oid='TestPInvoke'/>", "<qos/>");
         Console.WriteLine("subscribe() returned " + srq);
         
         //nc.publish("<key oid='TestPInvoke'/>", "HIII", "<qos/>");
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
         
         string[] urq = nc.unSubscribe("<key oid='TestPInvoke'/>", "<qos/>");
         Console.WriteLine("unSubscribe() returned " + urq[0]);         
         
         string[] erq = nc.erase("<key oid='C#C#C#'/>", "<qos/>");
         Console.WriteLine("erase() returned " + erq[0]);         
         
         Console.WriteLine("\nHit a key " + i);
         Console.ReadLine();
      }
      */
      bool drq = nc.disconnect("<qos/>");
      Console.WriteLine("TestPInvoke.cs: disconnect() returned " + drq);
      
      Console.WriteLine("DONE");
   }
   
   #region I_Callback Members
   public string OnUpdate(string cbSessionId, MsgUnit msgUnit) {
      Console.WriteLine("OnUpdate() invoked START ==================");
      if (callbackSessionId != cbSessionId)
         Console.WriteLine("Not authorized");
      Console.WriteLine(msgUnit.key);
      Console.WriteLine(msgUnit.getContentStr());
      Console.WriteLine(msgUnit.qos);
      string ret = "<qos><state id='OK'/></qos>";
      Console.WriteLine("OnUpdate() invoked DONE ===================");
      //throw new XmlBlasterException("user.update.illegalArgument", "A test exception from OnUpdate()");
      return ret;
   }
   #endregion
}
