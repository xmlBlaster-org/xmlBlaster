/*------------------------------------------------------------------------------
Name:      MassiveSubTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.stress;

import org.jutils.time.StopWatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;
import junit.framework.*;

/**
 * Test differents scenarios for a massive ammount of subscibers.
 *
 * <p>Test 5000 subscribers (or numSubscribers) on one connection.</p>
 * <p>Test 5000 subscribers (or numSubscribers) with maxSubPerCon per connection</p>
 * <p>Test 5000 subscribers (or numSubscruber) on one connection each.</p>
 * <p>Do it for IOP, RMI</p>
 *
 * <p>If withEmbedded is set to true will run without an embedded server.</p>
 *
 * @author Peter Antman
 */

public class MassiveSubTest extends TestCase implements I_Callback {
   private int numSubscribers = 5000;
   private int maxSubPerCon = 0;
   private boolean useOneConnection = false;
   private boolean withEmbedded = true;
   private int noToPub = 1;
   private int numToRec = numSubscribers * noToPub;
   private String ME = "MassiveSubTest";
   private Global glob;
   private LogChannel log;
   private int serverPort = 7615;
   private EmbeddedXmlBlaster serverThread;
   private boolean messageArrived = false;

   private final String publishOid1 = "dummy1";
   private XmlBlasterConnection oneConnection;
   private String oneName;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   class Client {
      String loginName;
      XmlBlasterConnection connection;
      String subscribeOid;
      boolean oneConnection;
   }
   private Client[] manyClients;
   private XmlBlasterConnection[] manyConnections;
   private StopWatch stopWatch = new StopWatch();


   public MassiveSubTest(String testName) {
      super(testName);
      Global glob_ = Global.instance();
      setProtoMax(glob_, "IOR", "500");
      init(glob_, testName, "testManyClients", true);
   }

   public MassiveSubTest(Global glob, String testName, String loginName, boolean useOneConnection) {
      super(testName);
      init(glob, testName, loginName, useOneConnection);
   }

   public void init(Global glob, String testName, String loginName, boolean useOneConnection) {
      this.glob = glob;
      this.log = this.glob.getLog("test");
      this.oneName = loginName;
      numSubscribers = glob.getProperty().get("numSubscribers", numSubscribers);
      maxSubPerCon = glob.getProperty().get("maxSubPerCon", maxSubPerCon);
      withEmbedded = glob.getProperty().get("withEmbedded", withEmbedded);
      noToPub = glob.getProperty().get("noToPub", noToPub);
      this.useOneConnection = useOneConnection;
      String clientProtocol = glob.getProperty().get("client.protocol", "IOR");
      try {
         glob.getProperty().set("client.protocol",clientProtocol);



      }catch(org.jutils.JUtilsException ex) {
         assertTrue("Could not setup test: " + ex, false);
      }
      ME = ME+":"+clientProtocol+(useOneConnection ? ":oneCon":":manyCon")+":"+numSubscribers + (maxSubPerCon>0?"/"+maxSubPerCon:"");

      numToRec = numSubscribers * noToPub;
   }
   
   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      log.info(ME, "Setting up test ...");
      if (withEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info(ME, "XmlBlaster is ready for testing a lots of subscribers");
      }


      numReceived = 0;
      try {
         oneConnection = new XmlBlasterConnection(); // Find orb
         ConnectQos loginQosW = new ConnectQos(glob); // "<qos></qos>"; During login this is manipulated (callback address added)
         // If we have many subs on one con, we must raise the max size of the callback queue!
         CbQueueProperty cbProp =loginQosW.getCbQueueProperty();
         cbProp.setMaxMsg(numSubscribers+1000);
         String passwd = "secret";
         oneConnection.login(oneName, passwd, null, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, "Login failed: " + e.toString());
          e.printStackTrace();
          assertTrue("Login failed: " + e.toString(), false);
      }
   }
   
   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      log.info(ME, "Tearing down");
      if (numReceived != numToRec) {
         log.error(ME, "numToRec=" + numToRec + " but numReceived=" + numReceived);
         assertEquals("numToRec=" + numToRec + " but numReceived=" + numReceived, numSubscribers, numReceived);
      }
      
      log.removeLogLevel("INFO");
      if (manyClients != null) {
         for (int ii=0; ii<numSubscribers; ii++) {
            Client sub = manyClients[ii];
            if (sub.oneConnection) {
               try {
                  if ( sub.connection != null) {
                     sub.connection.unSubscribe( "<key oid='"+sub.subscribeOid+"'/>",
                                                 "<qos/>");
                  } else {
                     oneConnection.unSubscribe( "<key oid='"+sub.subscribeOid+"'/>",
                                                "<qos/>");
                  } // end of else
                  
               }catch(XmlBlasterException ex) {
                  log.error(ME,"Could not unsubscribe: " +sub.subscribeOid+": " + ex);
               }
            }else {
               sub.connection.disconnect(null);
            }
         }
      }
      if ( manyConnections != null) {
         for ( int ii = 0;ii<manyConnections.length;ii++) {
            manyConnections[ii].disconnect(null);
         } // end of for ()
         
      } // end of if ()
      

      log.addLogLevel("INFO");
      
      {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
            "<key oid='" + publishOid1 + "' queryType='EXACT'>\n" +
            "</key>";
         String qos = "<qos></qos>";
         try {
            EraseReturnQos[] arr = oneConnection.erase(xmlKey, qos);
            assertEquals("Erase", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase-XmlBlasterException: " + e.getMessage()); }
      }
      
      oneConnection.disconnect(null);
      log.info(ME, "Logout done");
      if (withEmbedded) {
         try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
         EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
         
         // reset to default server port (necessary if other tests follow in the same JVM).
         Util.resetPorts();
      }
   }

   public void subcribeMany()
   {
      try {

      if (log.TRACE) log.trace(ME, "Subscribing ...");

      String passwd = "secret";

      SubscribeKey subKeyW = new SubscribeKey(glob, publishOid1);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid1 + "' queryType='EXACT'></key>";

      SubscribeQos subQosW = new SubscribeQos(glob); // "<qos></qos>";
      String subQos = subQosW.toXml();

      manyClients = new Client[numSubscribers];
      if (maxSubPerCon >0 ) {
         // Check if reasonably
          if (  numSubscribers %  maxSubPerCon!= 0) {
             assertTrue("numSubscribers not divadable by breakpoint", false);
          }

          manyConnections = new XmlBlasterConnection[numSubscribers/maxSubPerCon];
      } // end of if ()
      
         
      long usedBefore = getUsedServerMemory();

      log.info(ME, "Setting up " + numSubscribers + " subscriber clients ...");
      log.removeLogLevel("INFO");
      stopWatch = new StopWatch();
      for (int ii=0,ci=-1; ii<numSubscribers; ii++) {
         Client sub = new Client();
         sub.loginName = "Joe-" + ii;
         sub.oneConnection = useOneConnection;
         if (useOneConnection) {
            // Should we distribute among a few connections
            if (maxSubPerCon >0) {
               if (  ii % maxSubPerCon == 0) {
                  ci++;
                  try {
                     log.trace(ME,"Creating connection no: " +ci);
                     manyConnections[ci] = new XmlBlasterConnection(glob);
                     ConnectQos loginQosW = new ConnectQos(glob); // "<qos></qos>"; During login this is manipulated (callback address added)
                     // If we have many subs on one con, we must raise the max size of the callback queue!
                     CbQueueProperty cbProp =loginQosW.getCbQueueProperty();
                     // algo is maxSubPerCon*4
                     cbProp.setMaxMsg(maxSubPerCon*1000);//This means we have a backlog of 1000 messages per subscriber as i normal when each con only have one subscriber!
                     //cbProp.setMaxBytes(4000);
                     //cbProp.setOnOverflow(Constants.ONOVERFLOW_BLOCK);
                     //loginQosW.setSubjectCbQueueProperty(cbProp);
                     log.trace(ME,"Login qos: " +  loginQosW.toXml());
                     manyConnections[ci].login(sub.loginName, passwd, loginQosW, this);
                  }
                  catch (Exception e) {
                     log.error(ME, "Login failed: " + e.toString());
                     assertTrue("Login failed: " + e.toString(), false);
                  }

               } // end of if ()
               sub.connection = manyConnections[ci];
            } else {
               sub.connection = oneConnection;
            }
         }else {
            try {
               sub.connection = new XmlBlasterConnection(glob);
               ConnectQos loginQosW = new ConnectQos(glob); // "<qos></qos>"; During login this is manipulated (callback address added)
               sub.connection.login(sub.loginName, passwd, loginQosW, this);
            }
            catch (Exception e) {
               log.error(ME, "Login failed: " + e.toString());
               assertTrue("Login failed: " + e.toString(), false);
            }
         }
         try {
            sub.subscribeOid = sub.connection.subscribe(subKey, subQos).getSubscriptionId();
            log.trace(ME, "Client " + sub.loginName + " subscribed to " + subKeyW.getOid());
         } catch(XmlBlasterException e) {
            log.warn(ME, "XmlBlasterException: " + e.getMessage());
            assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
         }
         
         manyClients[ii] = sub;
      }
      double timeForLogins = (double)stopWatch.elapsed()/1000.; // msec -> sec
      log.addLogLevel("INFO");
      
      long usedAfter = getUsedServerMemory();
      long memPerLogin = (usedAfter - usedBefore)/numSubscribers;
      
      log.info(ME, numSubscribers + " subscriber clients are ready.");
      log.info(ME, "Server memory per login consumed=" + memPerLogin);
      log.info(ME, "Time " + (long)(numSubscribers/timeForLogins) + " logins/sec");
          
      } catch (Error e) {
         e.printStackTrace();
         log.error(ME,"Could not set up subscribers: " +e);
         throw e;
      } // end of try-catch
      
   }
   
   /**
    * Query xmlBlaster for its current memory consumption. 
    */
   long getUsedServerMemory() {
      String xmlKey = "<key oid='__cmd:?usedMem' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      try {
         MsgUnit[] msgArr = oneConnection.get(xmlKey, qos);
         String mem = new String(msgArr[0].getContent());
         return new Long(mem).longValue();
      } catch (XmlBlasterException e) {
         log.warn(ME, e.toString());
         return 0L;
      }
   }
   /**
    * TEST: Publish numToPub messages..
    * <p />
    * The returned publishOid1 is checked
    */
   public void publish()
   {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");
      
      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid1 + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
         "</key>";
      String senderContent = "Yeahh, i'm the new content";

      try {
         stopWatch = new StopWatch();
         for (int i = 0; i < noToPub;i++) {
            senderContent = senderContent+"-"+i;
            MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
            String tmp = oneConnection.publish(msgUnit).getKeyOid();
            assertEquals("Wrong publishOid1", publishOid1, tmp);
            log.info(ME, "Success: Publishing done for " + i +", returned oid=" + publishOid1);
         }
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         assertTrue("publishOne - XmlBlasterException: " + e.getMessage(), false);
      }
   }
   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      //log.info(ME, "Client " + loginName + " receiving update of message oid=" + updateKey.getOid() + "...");
      numReceived++;

      if (numReceived == numToRec) {
         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numReceived / elapsed);
         log.info(ME, numReceived + " messages updated, average messages/second = " + avg + stopWatch.nice());
      }
      return "";
   }
   
   /**
    * TEST: Construct a message and publish it,
    * all clients should receive an update. 
    */
   public void testManyClients()
   {
      log.plain(ME, "");
      log.info(ME, "TEST 1, many clients, useOneConnection="+useOneConnection);
      
      subcribeMany();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback
      
      publish();
      long delay = 2000L + 10 * numToRec;
      log.info(ME, "Waiting long enough for updates ..."+delay);
      Util.delay(delay);                          // Wait some time for callback to arrive ...

      if ( numReceived != numToRec ){
         // Warn and wait some more
         log.warn(ME,"Have not yet received more than " +numReceived+"/"+numToRec+" waiting some more");
         int midRec=numReceived;
         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numReceived / elapsed);
         log.info(ME, numReceived + " messages updated, average firts round messages/second = " + avg + stopWatch.nice(false));//Don't reset
         Util.delay(2L*delay); 
         //Lastt delay
         if ( numReceived != numToRec ){
         // Warn and wait some more
         log.warn(ME,"Have NOT yet received more than " +numReceived+"/"+numToRec+" waiting last round");
         avg = 0;
         elapsed = stopWatch.elapsed()-elapsed;
         if (elapsed > 0.)
            avg = (long)(1000.0 *( numReceived -midRec)/ elapsed);
         log.info(ME, numReceived-midRec + " messages updated this round, average second round messages/second = " + avg + stopWatch.nice(false));//Don't reset
         Util.delay(4L*delay); 
      }
         
      }

      log.info(ME, "Got messages:" +numReceived+"/"+numToRec);
      assertEquals("Wrong number of updates", numToRec, numReceived);
      
   }
   
   /**
    * Method is used by TestRunner to load these tests.
    *
    * <p>Warning! The default uses the embedded server, to give each round a equal chance. But it is MUCH slower than using a server in another VM.</p>
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      String loginName = "Tim";
      Global glob = Global.instance();
      // Test IOR many on one
      setProtoMax(glob,"IOR","0");
      suite.addTest(new MassiveSubTest(glob, "testManyClients", loginName,true));
      // Test IOR many on few
      setProtoMax(glob,"IOR","500");
      suite.addTest(new MassiveSubTest(glob, "testManyClients", loginName,true));
      // Test RMI many on one
      setProtoMax(glob,"RMI","0");
      suite.addTest(new MassiveSubTest(glob, "testManyClients", loginName,true));
      // Test RMI many on few
      setProtoMax(glob,"RMI","500");
      suite.addTest(new MassiveSubTest(glob, "testManyClients", loginName,true));
      // Test IOR many on many
      setProtoMax(glob,"IOR","0");
      suite.addTest(new MassiveSubTest(glob, "testManyClients", loginName,false));
      // Test RMI many on many
      setProtoMax(glob,"RMI","0");
      suite.addTest(new MassiveSubTest(glob, "testManyClients", loginName,false));
      
      return suite;
   }

   private static void setProtoMax(Global glob, String proto, String max) {
      try {
         glob.getProperty().set("client.protocol",proto);
         glob.getProperty().set("maxSubPerCon",max);
      }catch(org.jutils.JUtilsException ex) {
         assertTrue("Could not setup test: " + ex, false);
      }
   }

   public static void main(String[] args) {
      Global glob = new Global(args);
      setProtoMax(glob, "IOR", "500");
      MassiveSubTest m = new MassiveSubTest(glob, "testManyClients", "testManyClients", true);
      m.setUp();
      m.testManyClients();
      m.tearDown();
   }
   
} // MassiveSubTest
