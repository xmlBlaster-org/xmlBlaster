/*
@file     XmlBlasterAccessTest.cs
@comment  Access xmlBlaster from C# (Csharp)
@author   mr@marcelruff.info
@see      http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.csharp.html
*/
using System;
using NUnit.Framework;
using org.xmlBlaster.client;
using System.Collections;

[TestFixture]
public class XmlBlasterAccessTest : I_Callback, I_LoggingCallback
{
   private MsgUnitUpdate msgUnitUpdate = null;

   [SetUp]
   public void Init()
   {
      this.msgUnitUpdate = null;
   }

   [TearDown]
   public void Dispose()
   { /* ... */ }

   [Test]
   public void CheckInvalid()
   {
      I_XmlBlasterAccess xb = XmlBlasterAccessFactory.CreateInstance();
      xb.AddLoggingListener(null);
      xb.RemoveLoggingListener(null);
      string version = xb.GetVersion();
      Assert.IsNotNull(version);
      Console.WriteLine(version);
      string usage = xb.GetUsage();
      Assert.IsNotNull(usage);
      Console.WriteLine(usage);
   }

   [ExpectedException(typeof(XmlBlasterException))]
   [Test]
   public void CheckInvalidDisconnect()
   {
      I_XmlBlasterAccess xb = XmlBlasterAccessFactory.CreateInstance();
      xb.Disconnect(null);
      //xb.Connect(null, null);
   }

   [ExpectedException(typeof(XmlBlasterException))]
   [Test]
   public void CheckInvalidConnect()
   {
      I_XmlBlasterAccess xb = XmlBlasterAccessFactory.CreateInstance();
      xb.Connect(null, null);
   }

   /// <summary>
   /// This test expects a runngin xmlBlaster server!
   /// </summary>
   [Platform("NET-2.0")]
   [Test]
   public void CheckMethods()
   {
      I_XmlBlasterAccess xb = XmlBlasterAccessFactory.CreateInstance();
      string connectQos =
         "<qos>\n" +
         " <securityService type='htpasswd' version='1.0'>\n" +
         "   <user>fritz</user>\n" +
         "   <passwd>secret</passwd>\n" +
         " </securityService>\n" +
         "</qos>";
      ConnectReturnQos crq = xb.Connect(connectQos, this);
      Assert.IsNotNull(crq);

      SubscribeReturnQos srq = xb.Subscribe("<key oid='C#_test'/>", "<qos><updateOneway/></qos>");
      Assert.AreEqual("OK", srq.GetState());
      PublishReturnQos prq = xb.Publish("<key oid='C#_test'/>", "publish-1", "<qos/>");
      Assert.AreEqual("C#_test", prq.GetKeyOid());

      System.Threading.Thread.Sleep(1000);
      Assert.IsNotNull(this.msgUnitUpdate, "No update arrived");
      Assert.AreEqual("C#_test", this.msgUnitUpdate.GetUpdateKey().GetOid());

      UnSubscribeReturnQos[] urq = xb.UnSubscribe("<key oid='"+srq.GetSubscriptionId()+"'/>", "<qos/>");
      Assert.AreEqual(1, urq.Length);
      Assert.AreEqual(srq.GetSubscriptionId(), urq[0].GetSubscriptionId());

      {
         MsgUnitGet[] arr = xb.Get("<key oid='C#_test'/>", null);
         Assert.AreEqual(1, arr.Length);
         Assert.AreEqual("C#_test", arr[0].GetGetKey().GetOid());
      }

      EraseReturnQos[] erq = xb.Erase("<key oid='C#_test'/>", "<qos/>");
      Assert.AreEqual(1, erq.Length);
      Assert.AreEqual("C#_test", erq[0].GetKeyOid());

      {
         MsgUnitGet[] arr = xb.Get("<key oid='C#_test'/>", null);
         Assert.AreEqual(0, arr.Length);
      }

      xb.Disconnect("<qos/>");
   }

   #region I_Callback Members
   public string OnUpdate(string cbSessionId, MsgUnitUpdate msgUnit)
   {
      this.msgUnitUpdate = msgUnit;
      return "";
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
