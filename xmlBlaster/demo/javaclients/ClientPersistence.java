/*------------------------------------------------------------------------------
Name:      ClientPersistence.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package javaclients;

import org.jutils.log.LogChannel;
import org.jutils.init.Args;
import org.jutils.io.FileUtil;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;


/**
 * This client tests the persistence driver, the $lt;isDurable> flag.
 * Subscribes to a Message which is tagged $lt;isDurable> and will be set by the modultest of Xindice.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -classpath $CLASSPATH:~/java/xmlBlaster/classes/javaclients javaclients.ClientPersistence
 * </pre>
 */
public class ClientPersistence implements I_Callback
{
   private final static String ME = "ClientPersistence";
   private final LogChannel log;

   private final String senderName = "Tanja";
   private String publishOid = "amIdurable";
   private XmlBlasterConnection senderConnection = null;

   private static int numReceived = 0;

   /**
    * Constructs the ClientPersistence object.
    */
   public ClientPersistence(Global glob) {
      log = glob.getLog(null);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp()
   {
      try {
         String passwd = "secret";
         senderConnection = new XmlBlasterConnection();
         ConnectQos qos = new ConnectQos(null); // == "<qos></qos>";
         senderConnection.login(ME, passwd, qos, this);
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }


      String xmlKeyPub = "<key oid='" + publishOid + "' queryType='EXACT'>\n" + "</key>";

      try {
         senderConnection.subscribe(xmlKeyPub, "<qos></qos>");
      } catch(XmlBlasterException e2) {
         log.warn(ME, "XmlBlasterException: " + e2.getMessage());
      }
      log.trace(ME, "Subscribed to '" + publishOid + "' ...");
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown() {
      senderConnection.disconnect(null);
   }


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      //log.info(ME, "Receiving update of a message ...");
      log.info(ME, "Receiving update of message '" + publishOid +"'");

      numReceived += 1;

      log.plain("UpdateKey", updateKey.toXml());
      log.plain("content", (new String(content)).toString());
      log.plain("UpdateQos", updateQos.toXml());
      return "";
   }


   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(final long timeout, final int numWait)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (numReceived < numWait) {
         try {
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warn(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
   }


   /**
    * Invoke: java javaclients.ClientPersistence
    * <p />
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) System.exit(1);
      ClientPersistence cl = new ClientPersistence(glob);
      cl.setUp();
      cl.tearDown();
      cl.waitOnUpdate(10000000L, 10);
   }
}


