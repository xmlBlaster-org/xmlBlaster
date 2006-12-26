/*----------------------------------------------------------------------------
Name:      KeyTest.cs
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
   public class KeyTest
   {
      [Test]
      public void CheckComplete()
      {
         string clientTags =
            "<AGENT id='192.168.124.20' subId='1' type='generic'>"
            + "<DRIVER id='FileProof' pollingFreq='10'/>"
            + "</AGENT>";
         string keyStr =
            "<key oid='4711' contentMime='text/xml' contentMimeExtended='V1.0'>"
            + clientTags
            + "</key>";

         MsgKey key = new MsgKey(keyStr);
         Assert.AreEqual("4711", key.GetOid());
         Assert.AreEqual("text/xml", key.GetContentMime());
         Assert.AreEqual("V1.0", key.GetContentMimeExtended());
         string inner = key.GetClientTags();
         //We need a XMLUnit ...
         //Assert.AreSame(clientTags.Trim(), inner.Trim());
         StringAssert.Contains("AGENT", inner);
         StringAssert.Contains("DRIVER", inner);
      }

      [Test]
      public void CheckEmpty()
      {
         string keyStr = "";
         MsgKey key = new MsgKey(keyStr);
      }

      [ExpectedException(typeof(System.Xml.XmlException))]
      [Test]
      public void CheckInvalid()
      {
         string keyStr = "<keydstf>";
         MsgKey key = new MsgKey(keyStr);
         Assert.AreEqual("sdf", key.GetOid());
      }

      [Test]
      public void CheckStatusKey()
      {
         string keyStr = "<key oid='' queryType='XPATH' domain='some'>"
            + "  //something"
            + "</key>";
         StatusKey key = new StatusKey(keyStr);
         Assert.AreEqual("XPATH", key.GetQueryType());
         Assert.AreEqual("//something", key.GetQueryString().Trim());
         Assert.AreEqual("some", key.GetDomain());
      }
   }
}
