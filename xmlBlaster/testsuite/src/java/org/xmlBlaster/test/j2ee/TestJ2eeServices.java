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
package org.xmlBlaster.test.j2ee;
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

import org.xmlBlaster.j2ee.jmx.XmlBlasterService;
import org.xmlBlaster.j2ee.k2.BlasterManagedConnectionFactory;
import org.xmlBlaster.j2ee.k2.client.BlasterConnectionFactory;
import org.xmlBlaster.j2ee.k2.client.BlasterConnection;

import junit.framework.*;

import java.util.HashMap;
import javax.naming.Context;
/**
 * Test the j2ee services in combination.
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.2 $
 */

public class TestJ2eeServices extends TestCase implements I_Callback {
   private static String ME = "TestJ2eeServices";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestJ2eeServices.class.getName());

   private I_XmlBlasterAccess con = null;
   private String propertyFileName = "org/xmlBlaster/test/j2ee/blasterServer.properties";
   private String clientPropertyFileName = "org/xmlBlaster/test/j2ee/blasterClient.properties";
   private XmlBlasterService server;
   private int serverPort = 8624;
   private BlasterManagedConnectionFactory mcf;
   private BlasterConnectionFactory factory;
   private HashMap subscriberTable = new HashMap();
   private int[] subRec = new int[2];
   private String subscribeOid;
   private String subscribeOid2;
   private String name ="testsub";
   private String passwd = "secret";
   private int numReceived = 0;         // error checking

   public  TestJ2eeServices(){
      this(null, "TestJ2eeServices");
   }
   
   public  TestJ2eeServices(Global glob, String testName){
      super(testName);
      this.glob = (glob == null) ? Global.instance() : glob;

      
   }

   protected void setUpServer() throws Exception {
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY ,"org.xmlBlaster.test.j2ee.MemContext");
      server = new XmlBlasterService();
      server.setPropertyFileName(propertyFileName);
      server.setJNDIName("xmlBlaster/globalUtil");
      server.setPort(""+serverPort);
      server.start();
   }

   /**
    * we just skip anny pooling and goes directly?
    */
   protected void setUpK2() throws Exception {
      mcf = new BlasterManagedConnectionFactory();
      mcf.setUserName("test");
      mcf.setPassword("test");
      mcf.setClientProtocol("LOCAL");
      mcf.setIorPort(""+serverPort);
      mcf.setPropertyFileName(clientPropertyFileName);
      mcf.setJNDIName("xmlBlaster/globalUtil");

      factory = (BlasterConnectionFactory)mcf.createConnectionFactory();
   }

   protected void setUp() throws Exception {
      setUpServer();
      setUpK2();

      String[] args = {
         "-bootstrapPort",        // For all protocol we may use set an alternate server port
         "" + serverPort
      };
      glob.init(args);
      // Set up a subscriber
      try {
         log.info("Connecting ...");
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster
         
         String xmlKey = "<key oid='' queryType='XPATH'>\n" +
            "//TestLocalProtocol-AGENT" +
            "</key>";
         String sqos = "<qos><notify>false</notify></qos>"; // send no erase events
    
         subscribeOid = con.subscribe(xmlKey, sqos).getSubscriptionId() ;
         log.info("Success: Subscribe on subscriptionId=" + subscribeOid + " done");
         assertTrue("returned null subscriptionId", subscribeOid != null);
         
         subscriberTable.put(subscribeOid, new Integer(0));
         

      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }
   protected void tearDown() throws Exception 
   {
      log.info("TEST: tearing down");
      
      // FIXME: how do we destroy the managed connections?


      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...
      
      try {
         con.unSubscribe("<key oid='"+subscribeOid+"'/>",
                         "<qos/>");
         EraseReturnQos[] arr = con.erase("<key oid='' queryType='XPATH'>\n" +
                      "   /xmlBlaster/key/TestLocalProtocol-AGENT" +
                      "</key>", "<qos/>");
         assertEquals("Erase", 5, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
      
      con.disconnect(null);
      con=null;
      
      try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      server.stop();
      

   }

   public void testPublish()throws Exception {

      


         // Publish 5 messages
         // 5 for first sub
         // 1 for second sub
      BlasterConnection conn = null;
      for ( int i = 0; i<5;i++) {
         try {
            conn = factory.getConnection();
            String c = "<content>"+i+"</content>";
            String k = "<key oid='"+i+"' contentMime='text/xml'><TestLocalProtocol-AGENT id='"+i+"' type='generic'/></key>";
            log.info("Key: " +k);
            conn.publish(new MsgUnit(k, c.getBytes(), null));
         } catch(XmlBlasterException e) {
            log.warning("XmlBlasterException: " + e.getMessage());
            assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
         }finally {
            
            if ( conn != null) {
               conn.close();
            } // end of if ()
            
         } // end of finally
         
      }
      
      waitOnUpdate(subscribeOid,10000L, 5);
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
       suite.addTest(new TestJ2eeServices(new Global(), "testPublish"));
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
      TestJ2eeServices testSub = new TestJ2eeServices(glob, "TestLocalProtocol");      try {
         testSub.setUp();
         
         testSub.testPublish();
         
         
         testSub.tearDown();
      } catch (Throwable e) {
         e.printStackTrace();
      } // end of try-catch
   }
}// TestJ2eeServices
