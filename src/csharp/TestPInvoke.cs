/*
@file     TestPInvoke.cs
@comment  Access xmlBlaster from C# (Csharp)
@author   mr@marcelruff.info
@compile  csc /unsafe -debug+ -out:TestPInvoke.exe PInvokeCE.cs XmlBlasterAccess.cs TestPInvoke.cs
          gmcs /unsafe /define:"XMLBLASTER_MONO;FORCE_PINVOKECE_PLUGIN" -debug+ -out:TestPInvoke.exe PInvokeCE.cs TestPInvoke.cs XmlBlasterAccess.cs
@see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
*/
using System;
using System.Runtime.InteropServices;
using System.Threading;
using org.xmlBlaster.client;

public class TestPInvoke : I_Callback, I_LoggingCallback
{
   private I_XmlBlasterAccess xb;
   private const string callbackSessionId = "secretCb";

   static void Main(string[] argv)
   {
      Console.WriteLine("[TestPInvoke.cs] Startup");
      new TestPInvoke(argv);
   }

   public TestPInvoke(string[] argv)
   {
      xb = XmlBlasterAccessFactory.createInstance(argv);
      xb.addLoggingListener(this);

      string connectQos = String.Format(
         "<qos>\n" +
         " <securityService type='htpasswd' version='1.0'>\n" +
         "  <![CDATA[\n" +
         "   <user>fritz</user>\n" +
         "   <passwd>secret</passwd>\n" +
         "  ]]>\n" +
         " </securityService>\n" +
         " <queue relating='callback' maxEntries='50000' maxEntriesCache='10000'>\n" +
         "   <callback type='SOCKET' sessionId='{0}'>\n" +
         "   </callback>\n" +
         " </queue>\n" +
         "</qos>", callbackSessionId);  //"    socket://{1}:{2}"+
      log("Connecting with:" + connectQos);

      I_Callback callback = this;
      xb.connect(connectQos, callback);

      for (int run=0; run<2; run++) {

         string prq = xb.publish("<key oid='Hello'/>", "publish-1", "<qos/>");
         log("publish() returned " + prq);

         string srq = xb.subscribe("<key oid='Hello'/>", "<qos><updateOneway/></qos>");
         log("subscribe() returned " + srq);
         GC.Collect();
         GC.Collect();

         prq = xb.publish("<key oid='Hello'/>", "publish-2", "<qos/>");
         log("publish() returned " + prq);

         Thread.Sleep(1000);
         Console.WriteLine("There should be some updates, hit a key to continue ...");
         Console.ReadLine();

         srq = xb.subscribe("<key oid='TestPInvoke'/>", "<qos/>");
         log("subscribe() returned " + srq);

         srq = xb.subscribe("<key oid='TestPInvoke'/>", "<qos/>");
         log("subscribe() returned " + srq);

         string[] urq = xb.unSubscribe("<key oid='TestPInvoke'/>", "<qos/>");
         log("unSubscribe() returned");
         for (int i = 0; i < urq.Length; i++)
            log("unSubscribeReturn #" + i + "\n" + urq[i]);
         GC.Collect();
         GC.Collect();

         prq = xb.publish("<key oid='C#C#C#'/>", "more publishes", "<qos/>");
         log("publish() returned " + prq);

         MsgUnit[] arr = new MsgUnit[6];
         for (int i=0; i<arr.Length; i++)
            arr[i] = new MsgUnit("<key oid='C#C#'/>", "oneway-"+i, "<qos/>");
         xb.publishOneway(arr);
         log("publishOneway() send " + arr.Length + " messages");

         prq = xb.publish("<key oid='C#'/>", "HIIIHAAAA", "<qos/>");
         log("publish() returned " + prq);

         MsgUnit[] msgs = xb.get("<key oid='C#C#'/>", "<qos><history numEntries='4'/></qos>");
         log("get(C#C#) returned " + msgs.Length + " messages (get was limited to 4)");
         for (int i = 0; i < msgs.Length; i++)
            log(msgs[i].ToString());

         msgs = xb.get("<key oid='unknown'/>", "<qos><history numEntries='6'/></qos>");
         log("get(unknown) returned " + msgs.Length + " messages");

         string[] erq = xb.erase("<key queryType='XPATH'>//key</key>", "<qos/>");
         log("erase() returned");
         for (int i = 0; i < erq.Length; i++)
            log("eraseReturn #" + i + "\n" + erq[i]);

         string p = xb.ping("<qos/>");
         log("ping() returned " + p);

         bool b = xb.isConnected();
         log("isConnected() returned " + b);
      }

      bool drq = xb.disconnect("<qos/>");
      log("disconnect() returned " + drq);

      log("DONE");
   }

   #region I_Callback Members
   public string OnUpdate(string cbSessionId, MsgUnit msgUnit)
   {
      log("OnUpdate() received "+(msgUnit.isOneway()?"oneway ":"")+"message from xmlBlaster:");
      if (callbackSessionId != cbSessionId)
         log("Not authorized");
      log(msgUnit.ToString());
      return "<qos><state id='OK'/></qos>";
      //throw new XmlBlasterException("user.update.illegalArgument", "A test exception from OnUpdate()");
   }
   #endregion

   #region I_LoggingCallback Members
   public void OnLogging(LogLevel logLevel, string location, string message)
   {
      log(logLevel.ToString() + " " + location + ": " + message);
   }
   #endregion

   void log(String str)
   {
      Console.WriteLine(str);
      System.Diagnostics.Debug.WriteLine(str);
   }
}
