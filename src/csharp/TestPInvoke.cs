/*
@file     TestPInvoke.cs
@comment  Access xmlBlaster from C# (Csharp)
@author   mr@marcelruff.info
@compile  csc /unsafe -debug+ /define:FORCE_PINVOKECE,DOTNET2 -out:TestPInvoke.exe PInvokeCE.cs XmlBlasterAccess.cs TestPInvoke.cs
@see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
*/
using System;
using System.Runtime.InteropServices;
using System.Threading;
using org.xmlBlaster;

public class TestPInvoke : I_Callback
{
   private I_XmlBlasterAccess xb;
   private const string callbackSessionId = "secretCb";
   
   static void Main(string[] argv) {
       Console.WriteLine("[TestPInvoke.cs] main !!!!!!!!");
       new TestPInvoke(argv);
   }
   
   public TestPInvoke(string[] argv) {
      xb = XmlBlasterAccessFactory.createInstance(argv);

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
      log("Connecting with:" + connectQos);

      I_Callback callback = this;
      xb.connect(connectQos, callback);

      Console.WriteLine("Hit a key to subscribe to 'Hello' ...");
      Console.ReadLine();

      string srq = xb.subscribe("<key oid='Hello'/>", "<qos/>");
      log("subscribe() returned " + srq);

      Console.WriteLine("Hit a key to continue ...");
      Console.ReadLine();
      //Thread.Sleep(1000000000);
      /*      
            string srq = xb.subscribe("<key oid='TestPInvoke'/>", "<qos/>");
            log("subscribe() returned " + srq);

            srq = xb.subscribe("<key oid='TestPInvoke'/>", "<qos/>");
            log("subscribe() returned " + srq);

            string[] urq = xb.unSubscribe("<key oid='TestPInvoke'/>", "<qos/>");
            log("unSubscribe() returned");
            for (int i = 0; i < urq.Length; i++)
               log("unSubscribeReturn #" + i + "\n" + urq[i]);
            GC.Collect();
            GC.Collect();
      
            string prq = xb.publish("<key oid='C#C#C#'/>", "HIIIHAAAA", "<qos/>");
            log("publish() returned " + prq);

            prq = xb.publish("<key oid='C#C#'/>", "HIIIHAAAA", "<qos/>");
            log("publish() returned " + prq);

            prq = xb.publish("<key oid='C#'/>", "HIIIHAAAA", "<qos/>");
            log("publish() returned " + prq);
      
            MsgUnit[] msgs = xb.get("<key oid='C#C#C#'/>", "<qos><history numEntries='6'/></qos>");
            log("get(C#C#C#) returned " + msgs.Length + " messages");
            for (int i = 0; i < msgs.Length; i++)
               log(msgs[i].ToString());
      */
/*
      msgs = xb.get("<key oid='unknown'/>", "<qos><history numEntries='6'/></qos>");
      log("get(unknown) returned " + msgs.Length + " messages");

      string[] erq = xb.erase("<key queryType='XPATH'>//key</key>", "<qos/>");
      log("erase() returned");
      for (int i = 0; i < erq.Length; i++)
         log("eraseReturn #"+i+"\n"+erq[i]);

      string p = xb.ping("<qos/>");
      log("ping() returned " + p);

      bool b = xb.isConnected();
      log("isConnected() returned " + b);
*/
      /*
      for (int i=0; i<5; i++) {
         string srq = xb.subscribe("<key oid='TestPInvoke'/>", "<qos/>");
         log("subscribe() returned " + srq);
         
         //xb.publish("<key oid='TestPInvoke'/>", "HIII", "<qos/>");
         string prq = xb.publish("<key oid='C#C#C#'/>", "HIIIHAAAA", "<qos/>");
         log("publish() returned " + prq);         
         
         xb.publish("<key oid='C#C#C#'/>", "HIIIHOOO", "<qos/>");
         log("publish() returned " + prq);         
         
         MsgUnit[] msgs = xb.get("<key oid='C#C#C#'/>", "<qos><history numEntries='6'/></qos>");
         log("get() returned " + msgs.Length + " messages");         
         
         string p = xb.ping("<qos/>");
         log("ping() returned " + p);         
         
         bool b = xb.isConnected();
         log("isConnected() returned " + b);         
         
         string[] urq = xb.unSubscribe("<key oid='TestPInvoke'/>", "<qos/>");
         log("unSubscribe() returned " + urq[0]);         
         
         string[] erq = xb.erase("<key oid='C#C#C#'/>", "<qos/>");
         log("erase() returned " + erq[0]);         
         
         log("\nHit a key " + i);
         Console.ReadLine();
      }
      */
      bool drq = xb.disconnect("<qos/>");
      log("disconnect() returned " + drq);
      
      log("DONE");
   }
   
   #region I_Callback Members
   public string OnUpdate(string cbSessionId, MsgUnit msgUnit) {
      log("OnUpdate() invoked START ==================");
      if (callbackSessionId != cbSessionId)
         log("Not authorized");
      log(msgUnit.key);
      log(msgUnit.getContentStr());
      log(msgUnit.qos);
      string ret = "<qos><state id='OK'/></qos>";
      log("OnUpdate() invoked DONE ===================");
      //throw new XmlBlasterException("user.update.illegalArgument", "A test exception from OnUpdate()");
      return ret;
   }
   #endregion

   void log(String str) {
      if (xb != null)
         xb.log("[TestPInvoke.cs] " + str);
      else {
         Console.WriteLine("[TestPInvoke.cs] "+str);
         System.Diagnostics.Debug.WriteLine("[TestPInvoke.cs] " + str);
      }
   }
}
