/*
 * Copyright (c) 2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * $Id$
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.xmlBlaster.test.client;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.j2ee.util.GlobalUtil;
import org.xmlBlaster.test.Util;


import junit.framework.*;

import java.util.HashMap;
/**
 * Test that local (in vm) client protocol works.
 *
 * <p>We start an embedded server so that we have access to the engine.Global.</p>
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.2 $
 */

public class TestLocalProtocol extends TestCase implements I_Callback {
   private static String ME = "TestLocalProtocol";
   private final Global glob;
   private GlobalUtil globalUtil;
   private static Logger log = Logger.getLogger(TestLocalProtocol.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private int numReceived = 0;         // error checking
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7624;

   private HashMap subscriberTable = new HashMap();
   private int[] subRec = new int[2];
   String subscribeOid;
   String subscribeOid2;

   
   public TestLocalProtocol (){
      this(null, "TestLocalProtocol", "TestLocalProtocol");
   }

   public TestLocalProtocol (Global glob, String testName, String name){
      super(testName);
      this.glob = (glob == null) ? Global.instance() : glob;

      this.name = name;

   }
   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread, configured with
    * the local drivers,
    * <p />
    * Then we connect as a client
    */
   protected void setUp()
   {
      String[] args = {
         "-bootstrapPort",        // For all protocol we may use set an alternate server port
         "" + serverPort,
         "-plugin/socket/port",
         "" + (serverPort-1),
         "-plugin/rmi/registryPort",
         "" + (serverPort-2),
         "-plugin/xmlrpc/port",
         "" + (serverPort-3),
         "-ClientProtocolPlugin[LOCAL][1.0]",
         "org.xmlBlaster.client.protocol.local.LocalConnection",
         "-ClientCbServerProtocolPlugin[LOCAL][1.0]",
         "org.xmlBlaster.client.protocol.local.LocalCallbackImpl",
         "-CbProtocolPlugin[LOCAL][1.0]",
         "org.xmlBlaster.protocol.local.CallbackLocalDriver",
         "-protocol",
         "LOCAL",
         "-admin.remoteconsole.port",
         "0"
      };
      /*
,
         "-trace",
         "false"
      */
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(args);

      globalUtil = new GlobalUtil( serverThread.getMain().getGlobal() );
      Global runglob = globalUtil.getClone( glob );

      log.info("XmlBlaster is ready for testing subscribe MIME filter");

      try {
         log.info("Connecting ...");
         con = runglob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(runglob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.severe("Can't connect to xmlBlaster: " + e.toString());
      }

      // Subscribe to a message with a supplied filter
      try {
         String xmlKey = "<key oid='' queryType='XPATH'>\n" +
            "//TestLocalProtocol-AGENT" +
            "</key>";
         String qos = "<qos><notify>false</notify></qos>"; // send no erase events
    
         subscribeOid = con.subscribe(xmlKey, qos).getSubscriptionId() ;
         log.info("Success: Subscribe on subscriptionId=" + subscribeOid + " done");
         assertTrue("returned null subscriptionId", subscribeOid != null);
         
         subscriberTable.put(subscribeOid, new Integer(0));
         
         xmlKey = "<key oid='' queryType='XPATH'>\n" +
            "//TestLocalProtocol-AGENT[@id='3']" +
            "</key>";

         subscribeOid2 = con.subscribe(xmlKey, qos).getSubscriptionId() ;
         log.info("Success: Subscribe on subscriptionId=" + subscribeOid2 + " done");
         assertTrue("returned null subscriptionId", subscribeOid2 != null);
         
         subscriberTable.put(subscribeOid2, new Integer(1));

      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

      /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      log.info("TEST: tearing down");
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...
      
      try {
         con.unSubscribe("<key oid='"+subscribeOid+"'/>",
                         "<qos/>");
         con.unSubscribe("<key oid='"+subscribeOid2+"'/>",
                         "<qos/>");
         EraseReturnQos[] arr = con.erase("<key oid='' queryType='XPATH'>\n" +
                      "   /xmlBlaster/key/TestLocalProtocol-AGENT" +
                      "</key>", "<qos/>");
         assertEquals("Erase", 5, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
      
      con.disconnect(null);
      con=null;
      
      try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;
      
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   public void testPublish()
   {
      log.info("testPublish...");

      log.info("TEST 1");
      try {
         // Publish 5 messages
         // 5 for first sub
         // 1 for second sub
         for ( int i = 0; i<5;i++) {
            String c = "<content>"+i+"</content>";
            String k = "<key oid='"+i+"' contentMime='text/xml'><TestLocalProtocol-AGENT id='"+i+"' type='generic'/></key>";
            log.info("Key: " +k);
            con.publish(new MsgUnit(k, c.getBytes(), null));
         }
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(subscribeOid,10000L, 5);
      waitOnUpdate(subscribeOid2,10000L, 1);

   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of a message " + updateKey.getOid() + " for subId: " + updateQos.getSubscriptionId() );
      int ii = ((Integer)subscriberTable.get(updateQos.getSubscriptionId())).intValue();
      log.fine("Got message " + new String(content));
      subRec[ii]++;
      numReceived++;
      return "";
   }
   
   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(String subId,final long timeout, final int numWait)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      int ii = ((Integer)subscriberTable.get(subId)).intValue();
      // check if too few are arriving
      while (subRec[ii] < numWait) {
         try { Thread.currentThread().sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.currentThread().sleep(timeout); } catch( InterruptedException i) {}
      assertEquals("Wrong number of messages arrived", numWait, subRec[ii]);
      log.info("Found correct rec messages for: " + subId);
      subRec[ii]= 0;
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestLocalProtocol(new Global(), "testPublish", "Tim"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.mime.TestXPathSubscribeFilter
    *   java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.mime.TestXPathSubscribeFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestLocalProtocol testSub = new TestLocalProtocol(glob, "TestLocalProtocol", "Tim");
      testSub.setUp();
      try {
         testSub.testPublish();
      } catch (Throwable e) {
         e.printStackTrace();
      } // end of try-catch

      testSub.tearDown();
   }
}// TestLocalProtocol
