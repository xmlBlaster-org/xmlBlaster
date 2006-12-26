/*----------------------------------------------------------------------------
Name:      QosTest.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test cases for Qos.cs
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      12/2006
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
using System;
using System.Collections.Generic;
using System.Text;
using NUnit.Framework;
using System.Reflection;
using System.Collections;

namespace org.xmlBlaster.client
{
   [TestFixture]
   public class QosTest
   {
      [Test]
      public void CheckComplete()
      {
         byte[] bytes = new byte[2];
         bytes[0] = (byte)199;
         bytes[1] = (byte)4;
         string base64 = System.Convert.ToBase64String(bytes);
         string qosStr = "<qos> <!-- UpdateQos -->"
            + "  <state id='ERASED'/>"
            + "  <sender>/node/heron/client/joe/-2  </sender>"
            + "  <priority>7  </priority>"
            + "  <subscribe id='__subId:heron-2'/>"
            + "  <rcvTimestamp nanos='1007764305862000002'>"
            + "    2001-12-07 23:31:45.862000002 <!-- The nanos from above but human readable -->"
            + "  </rcvTimestamp>"
            + "  <expiration lifeTime='129595811' remainingLife='1200'/>"
            + "  <queue index='3' size='6'/> <!-- If queued messages are flushed on login -->"
            + "  <redeliver> 4  </redeliver> <!-- Only sent if message sending had previous errors -->"
            + "  <clientProperty name='myTransactionId'>0x23345</clientProperty>"
            + "  <clientProperty name='myDescription' encoding='base64' charset='windows-1252'>QUUgaXMgJ8QnDQpPRSBpcyAn1icNCnNzIGlzICffJw==</clientProperty>"
            + "  <clientProperty name='myAge' type='int'>12</clientProperty>"
            + "  <clientProperty name='nothing'/>"
            + "  <clientProperty name='invalid' type='int'>ABC</clientProperty>"
            + "  <clientProperty name='somebytes' encoding='base64' type='byte[]'>" + base64 + "</clientProperty>"
            + "  <route> <!-- Routing information in cluster environment -->"
            + "    <node id='avalon' stratum='1' timestamp='1068026303739000001' dirtyRead='false'/>"
            + "    <node id='heron' stratum='0' timestamp='1068026303773000001' dirtyRead='false'/>"
            + "  </route>"
            + "</qos>";
         UpdateQos qos = new UpdateQos(qosStr);
         Assert.AreEqual("ERASED", qos.GetState());
         Assert.AreEqual(false, qos.IsOk());
         Assert.AreEqual(true, qos.IsErased());
         SessionName sessionName = qos.GetSender();
         Assert.AreEqual("/node/heron/client/joe/session/-2", sessionName.ToString());
         Assert.AreEqual(-2, sessionName.GetPublicSessionId());
         Assert.AreEqual("joe", sessionName.GetLoginName());
         Assert.AreEqual("client/joe/session/-2", sessionName.GetRelativeName());
         Assert.AreEqual(7, qos.GetPriority());
         Assert.AreEqual("__subId:heron-2", qos.GetSubscriptionId());
         Assert.AreEqual("2001-12-07 23:31:45.862000002", qos.GetRcvTime());
         Assert.AreEqual(1007764305862000002, qos.GetRcvTimeNanos());
         Assert.AreEqual(129595811, qos.GetLifeTime());
         Assert.AreEqual(3, qos.GetQueueIndex());
         Assert.AreEqual(6, qos.GetQueueSize());
         Assert.AreEqual(4, qos.GetRedeliver());
         Assert.AreEqual("0x23345", qos.GetClientProperty("myTransactionId", ""));
         Assert.AreEqual("AE is '�'\r\nOE is '�'\r\nss is '�'", qos.GetClientProperty("myDescription", ""));
         Assert.AreEqual(12, qos.GetClientProperty("myAge", 0));
         Assert.AreEqual("", qos.GetClientProperty("nothing", ""));
         Assert.AreEqual("", qos.GetClientProperty("aaa", ""));
         Assert.AreEqual(18, qos.GetClientProperty("invalid", 18));
         Assert.AreEqual(bytes, qos.GetClientProperty("somebytes", new byte[0]));
         Assert.AreEqual(6, qos.GetClientProperties().Count);
         ClientProperty myDescription = qos.GetClientProperty("myDescription");
         Assert.IsNotNull(myDescription);
         Assert.AreEqual(true, myDescription.IsBase64());
         Assert.AreEqual(ClientProperty.ENCODING_BASE64, myDescription.GetEncoding());
         Assert.AreEqual(ClientProperty.TYPE_STRING, myDescription.GetValueType());
         Assert.AreEqual("myDescription", myDescription.GetName());
      }

      [Test]
      public void CheckEmpty()
      {
         string qosStr = "";
         UpdateQos qos = new UpdateQos(qosStr);
         Assert.AreEqual("OK", qos.GetState());
         Assert.AreEqual(true, qos.IsOk());
         Assert.AreEqual(false, qos.IsErased());
         Assert.AreEqual(5, qos.GetPriority());
         SessionName sessionName = qos.GetSender();
         Assert.IsNull(sessionName);
         Assert.AreEqual("", qos.GetSubscriptionId());
         Assert.AreEqual("", qos.GetRcvTime());
         Assert.AreEqual(-1L, qos.GetLifeTime());
         Assert.AreEqual(0, qos.GetQueueIndex());
         Assert.AreEqual(0, qos.GetQueueSize());
         Assert.AreEqual(0, qos.GetRedeliver());
         Assert.AreEqual("", qos.GetClientProperty("myTransactionId", ""));
         Assert.AreEqual(0, qos.GetClientProperties().Count);
      }

      [ExpectedException(typeof(System.Exception))]
      [Test]
      public void CheckInvalid()
      {
         string qosStr = "<qos>"
            + "  <sender>/node/heron/clXXXient/joe/-2</sender>"
            + "</qos>";
         UpdateQos qos = new UpdateQos(qosStr);
         SessionName sessionName = qos.GetSender();
         Assert.AreEqual("/node/heron/client/joe/session/-2", sessionName.ToString());
      }

      [Test]
      public void CheckSubscribeReturnQos()
      {
         string qosStr = "<qos>"
            + "  <state id='OK' info='QUEUED'/>"
            + "  <subscribe id='__subId:heron-2'/>"
            + "</qos>";
         SubscribeReturnQos qos = new SubscribeReturnQos(qosStr);
         Assert.AreEqual("OK", qos.GetState());
         Assert.AreEqual("QUEUED", qos.GetStateInfo());
         Assert.AreEqual("__subId:heron-2", qos.GetSubscriptionId());
      }

      [Test]
      public void CheckUnSubscribeReturnQos()
      {
         string qosStr = "<qos>"
            + "  <state id='OK' info='QUEUED'/>"
            + "  <subscribe id='__subId:heron-2'/>"
            + "</qos>";
         UnSubscribeReturnQos qos = new UnSubscribeReturnQos(qosStr);
         Assert.AreEqual("OK", qos.GetState());
         Assert.AreEqual("QUEUED", qos.GetStateInfo());
         Assert.AreEqual("__subId:heron-2", qos.GetSubscriptionId());
      }

      [Test]
      public void CheckEraseReturnQos()
      {
         string qosStr = "<qos>"
            + "  <state id='OK' info='QUEUED'/>"
            + "  <key oid='MyTopic'/>"
            + "</qos>";
         EraseReturnQos qos = new EraseReturnQos(qosStr);
         Assert.AreEqual("OK", qos.GetState());
         Assert.AreEqual("QUEUED", qos.GetStateInfo());
         Assert.AreEqual("MyTopic", qos.GetKeyOid());
      }

      [Test]
      public void CheckPublishReturnQos()
      {
         string qosStr = "<qos>"
            + "  <state id='OK' info='QUEUED'/>"
            + "  <key oid='MyTopic'/>"
            + "  <rcvTimestamp nanos='1007764305862000002'>"
            + "    2001-12-07 23:31:45.862000002"
            + "  </rcvTimestamp>"
            + "</qos>";
         PublishReturnQos qos = new PublishReturnQos(qosStr);
         Assert.AreEqual("OK", qos.GetState());
         Assert.AreEqual("QUEUED", qos.GetStateInfo());
         Assert.AreEqual("MyTopic", qos.GetKeyOid());
         Assert.AreEqual("2001-12-07 23:31:45.862000002", qos.GetRcvTime());
         Assert.AreEqual(1007764305862000002, qos.GetRcvTimeNanos());
      }
   }
}
