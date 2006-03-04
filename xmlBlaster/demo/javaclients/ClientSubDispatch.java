/*------------------------------------------------------------------------------
Name:      ClientSubDispatch.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.util.MsgUnit;


/**
 * This client demonstrates the method subscribe() with a later publish().
 * <p />
 * We use a subscribe variant, where for every subscribe we define a
 * specialized update method.<br />
 * Like this not all callback messages arrive in a centralized update()
 * with the need to look into them and decide why the arrived.
 * <p />
 * This demo uses the I_XmlBlasterAccess helper class, which hides the raw
 * CORBA/RMI/XMLRPC nastiness and allows this client side dispatching.
 * <br />
 * Invoke examples:<br />
 * <pre>
 *    java -cp ../../lib/xmlBlaster.jar javaclients.ClientSubDispatch
 *
 *    java javaclients.ClientSubDispatch -loginName Jeff -dispatch/connection/protocol RMI
 *
 *    java javaclients.ClientSubDispatch -help
 * </pre>
 */
public class ClientSubDispatch implements I_Callback
{
   private static String ME = "ClientSubDispatch";
   private final Global glob;
   private static Logger log = Logger.getLogger(ClientSubDispatch.class.getName());
   private int numReceived1 = 0;         // error checking
   private int numReceived2 = 0;         // error checking

   public ClientSubDispatch(Global glob) {
      this.glob = glob;


      try {
         ConnectQos loginQos = new ConnectQos(null); // creates "<qos></qos>" string
         I_XmlBlasterAccess blasterConnection = glob.getXmlBlasterAccess();
         blasterConnection.connect(loginQos, this);  // Now we are connected to xmlBlaster MOM server.

         // Subscribe to messages with XPATH using some helper classes
         log.info("Subscribing #1 for anonymous callback class using XPath syntax ...");
         SubscribeKey key = new SubscribeKey(glob, "//DispatchTest", "XPATH");
         SubscribeQos qos = new SubscribeQos(glob);
         blasterConnection.subscribe(key, qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info("Receiving message with specialized update() #1 ...");
                  numReceived1++;
                  System.out.println(updateKey.toXml());
                  System.out.println((new String(content)).toString());
                  System.out.println(updateQos.toXml());
                  return "";
               }
            });


         log.info("Subscribing #2 for anonymous callback class using XPath syntax ...");
         key = new SubscribeKey(glob, "A message id");
         blasterConnection.subscribe(key, qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info("Receiving message with specialized update() #2 ...");
                  numReceived2++;
                  System.out.println(updateKey.toXml());
                  System.out.println((new String(content)).toString());
                  System.out.println(updateQos.toXml());
                  return "";
               }
            });


         // Construct a message and publish it ...
         String publishOid1 = "";
         // This time, as an example, we don't use the wrapper helper classes,
         // and create the string 'by hand':
         String xmlKey =   "<key oid='' contentMime='text/xml'>\n" +
                           "   <DispatchTest>" +
                           "   </DispatchTest>" +
                           "</key>";
         String content = "Some content #1";
         MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), "<qos></qos>");
         publishOid1 = blasterConnection.publish(msgUnit).getKeyOid();
         log.info("Publishing done, returned oid=" + publishOid1);

         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait a second

         String publishOid2 = "";
         xmlKey = "<key oid='A message id' contentMime='text/xml'>\n" +
                  "</key>";
         content = "Some content #2";
         msgUnit = new MsgUnit(xmlKey, content.getBytes(), "<qos></qos>");
         publishOid2 = blasterConnection.publish(msgUnit).getKeyOid();
         log.info("Publishing done, returned oid=" + publishOid2);


         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait a second

         if (numReceived1 == 1)
            log.info("Success, got Callback #1 after publishing");
         else
            log.severe(numReceived1 + " callbacks arrived, did expect one after a simple subscribe with a publish");

         if (numReceived2 == 1)
            log.info("Success, got Callback #2 after publishing");
         else
            log.severe(numReceived2 + " callbacks arrived, did expect one after a simple subscribe with a publish");


         // cleaning up .... erase() the previous published message
         xmlKey = "<key oid='" + publishOid1 + "' queryType='EXACT'>\n" +
                  "</key>";
         EraseReturnQos[] strArr = blasterConnection.erase(xmlKey, "<qos></qos>");
         if (strArr.length != 1) log.severe("Erased " + strArr.length + " message.");

         xmlKey = "<key oid='" + publishOid2 + "' queryType='EXACT'>\n" +
                  "</key>";
         strArr = blasterConnection.erase(xmlKey, "<qos></qos>");
         if (strArr.length != 1) log.severe("Erased " + strArr.length + " message.");

         blasterConnection.disconnect(null);
      }
      catch(XmlBlasterException e) {
         log.severe("XmlBlasterException: " + e.getMessage());
      }
      catch (Exception e) {
         log.severe("Client failed: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.severe("Received unexpected asynchronous callback-update from xmlBlaster from publisher " + updateQos.getSender() + ":");
      log.severe(updateKey.toXml() + "\n" + updateQos.toXml());
      return "";
   }

   public static void main(String args[]) {
      new ClientSubDispatch(new Global(args));
   }
} // ClientSubDispatch

