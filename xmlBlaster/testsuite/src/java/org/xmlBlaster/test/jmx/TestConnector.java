package org.xmlBlaster.test.jmx;

import java.io.IOException;
import junit.framework.*;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.util.admin.extern.SerializeHelper;
import org.xmlBlaster.util.admin.extern.MethodInvocation;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.*;

import org.xmlBlaster.client.key.*;


import org.xmlBlaster.client.I_Callback;

import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.util.XmlBlasterException;

import java.util.Properties;

//import org.xmlBlaster.test.Util;

public class TestConnector  extends TestCase implements I_Callback {
  private final int serverPort = 3424;
  private final static String ME = "TestConnector";
  private Global glob = null;
  private LogChannel log = null;
  private I_XmlBlasterAccess returnCon;
  private I_XmlBlasterAccess invokeCon;

  private MsgUnit msg = null;
  private static String port = "3424";

  SerializeHelper sh = null;
  MethodInvocation mi = null;

  public TestConnector(String testName)
   {
       super(testName);
   }


   protected void setUp()
   {
     if (this.glob == null) this.glob = new Global().instance();
     this.log = this.glob.getLog("test");
     log.info(ME,"setUp of TestConnector...");
     //connect to embedded xmlBlaster

    Properties prop = new Properties();
    prop.setProperty("bootstrapPort",port);
    prop.setProperty("bootstrapHostname","localhost");

    glob.init(prop);

    invokeCon = glob.getXmlBlasterAccess();
    returnCon = glob.getXmlBlasterAccess();

//    log.info(ME,"Connecting to embedded xmlBlaster on port "+ port +" Adresse " + addr.getAddress());
    try {
      ConnectQos qos = new ConnectQos(glob, "InternalConnector", "connector");
      returnCon.connect(qos, this);
    }
    catch (XmlBlasterException ex) {
      assertTrue("Error when connecting to xmlBlaster " + ex.toString(), false);
      log.error(ME,"Error when connecting to xmlBlaster " + ex.toString());
    }
    SubscribeKey subKey = new SubscribeKey(this.glob, "xmlBlasterMBeans_Return");

    SubscribeQos sQos = new SubscribeQos(this.glob);
    sQos.setWantLocal(false);
    try {
      returnCon.subscribe(subKey, sQos);
    }
    catch (XmlBlasterException ex) {
      assertTrue("Error when subscribing to xmlBlaster " + ex.toString(), false);
      log.error(ME,"Error when subscribing to xmlBlaster " + ex.toString());
    }
     sh = new SerializeHelper(glob);

     if (invokeCon.isConnected() && returnCon.isConnected()) {log.info(ME,"connection establisheld");}
     else log.warn(ME,"Couldnt connect to server on port " + port);
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestConnector("testConnector"));
       return suite;
   }

   /**
    * TEST:
    * <p />
    */
   public void testConnector() {
     try {
       log.info(ME,"Creating TestMessage");
       mi = new MethodInvocation();
       mi.setMethodName("getDefaultDomain");
       log.info(ME,"new MethodInvocation build " + mi.getMethodName());
       PublishReturnQos rqos = invokeCon.publish(new MsgUnit("<key oid='xmlBlasterMBeans_Invoke'/>",sh.serializeObject(mi),"<qos/>"));
       log.info(ME,"Publish test Message to jmx-topic..");
       }
    catch (XmlBlasterException ex) {
      ex.printStackTrace();
    }
    catch (IOException ex) {
      log.error(ME,"Error when creating methodInvocation " + ex.toString());
      ex.printStackTrace();
    }

   }


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of a message, checking ...");
      MethodInvocation mi = null;
      try {
        mi = (MethodInvocation) sh.deserializeObject(content);
      }
      catch (IOException ex) {
        log.error(ME,"Error when deserializing object");
      }
      Object obj = mi.getReturnValue();
      log.info(ME,"Received Object: " + obj);
      if (obj.toString().length()>0) log.info(ME,"Success... Received Domainname: " + obj);
      else {
        log.error(ME,"Error when receiving returning object...");
        assertTrue("Error when receiving returning object...", false);
      }
      return "";
   }

}