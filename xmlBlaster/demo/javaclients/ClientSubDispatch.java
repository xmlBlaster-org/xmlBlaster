/*------------------------------------------------------------------------------
Name:      ClientSubDispatch.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientSubDispatch.java,v 1.5 2002/03/18 00:30:22 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client demonstrates the method subscribe() with a later publish().
 * <p />
 * We use a subscribe variant, where for every subscribe we define a
 * specialized update method.<br />
 * Like this not all callback messages arrive in a centralized update()
 * with the need to look into them and decide why the arrived.
 * <p />
 * This demo uses the XmlBlasterConnection helper class, which hides the raw
 * CORBA/RMI/XML-RPC nastiness and allows this client side dispatching.
 * <br />
 * Invoke examples:<br />
 * <pre>
 *    java -cp ../../lib/xmlBlaster.jar javaclients.ClientSubDispatch
 *
 *    java javaclients.ClientSubDispatch -name Jeff -client.protocol RMI
 *
 *    java javaclients.ClientSubDispatch -help
 * </pre>
 */
public class ClientSubDispatch implements I_Callback
{
   private static String ME = "ClientSubDispatch";
   private int numReceived1 = 0;         // error checking
   private int numReceived2 = 0;         // error checking


   public ClientSubDispatch(String args[])
   {
      initArgs(args); // Initialize command line argument handling (this is optional)

      try {
         // check if parameter -name <userName> is given at startup of client
         String loginName = Args.getArg(args, "-name", ME);
         String passwd = Args.getArg(args, "-passwd", "secret");
         ConnectQos loginQos = new ConnectQos(); // creates "<qos></qos>" string

         XmlBlasterConnection blasterConnection = new XmlBlasterConnection(args);
         blasterConnection.login(loginName, passwd, loginQos, this);
         // Now we are connected to xmlBlaster MOM server.



         // Subscribe to messages with XPATH using some helper classes
         Log.info(ME, "Subscribing #1 for anonymous callback class using XPath syntax ...");
         SubscribeKeyWrapper key = new SubscribeKeyWrapper("//DispatchTest", "XPATH");
         SubscribeQosWrapper qos = new SubscribeQosWrapper();
         blasterConnection.subscribe(key.toXml(), qos.toXml(), new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) {
                  Log.info(ME, "Receiving message with specialized update() #1 ...");
                  numReceived1++;
                  Log.plain("UpdateKey", updateKey.toXml());
                  Log.plain("content", (new String(content)).toString());
                  Log.plain("UpdateQoS", updateQoS.toXml());
                  return "";
               }
            });


         Log.info(ME, "Subscribing #2 for anonymous callback class using XPath syntax ...");
         key = new SubscribeKeyWrapper("A message id");
         blasterConnection.subscribe(key.toXml(), qos.toXml(), new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) {
                  Log.info(ME, "Receiving message with specialized update() #2 ...");
                  numReceived2++;
                  Log.plain("UpdateKey", updateKey.toXml());
                  Log.plain("content", (new String(content)).toString());
                  Log.plain("UpdateQoS", updateQoS.toXml());
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
         MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
         publishOid1 = blasterConnection.publish(msgUnit);
         Log.info(ME, "Publishing done, returned oid=" + publishOid1);

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait a second

         String publishOid2 = "";
         xmlKey = "<key oid='A message id' contentMime='text/xml'>\n" +
                  "</key>";
         content = "Some content #2";
         msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
         publishOid2 = blasterConnection.publish(msgUnit);
         Log.info(ME, "Publishing done, returned oid=" + publishOid2);


         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait a second

         if (numReceived1 == 1)
            Log.info(ME, "Success, got Callback #1 after publishing");
         else
            Log.error(ME, numReceived1 + " callbacks arrived, did expect one after a simple subscribe with a publish");

         if (numReceived2 == 1)
            Log.info(ME, "Success, got Callback #2 after publishing");
         else
            Log.error(ME, numReceived2 + " callbacks arrived, did expect one after a simple subscribe with a publish");


         // cleaning up .... erase() the previous published message
         xmlKey = "<key oid='" + publishOid1 + "' queryType='EXACT'>\n" +
                  "</key>";
         String[] strArr = blasterConnection.erase(xmlKey, "<qos></qos>");
         if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " message.");

         xmlKey = "<key oid='" + publishOid2 + "' queryType='EXACT'>\n" +
                  "</key>";
         strArr = blasterConnection.erase(xmlKey, "<qos></qos>");
         if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " message.");


         blasterConnection.logout();
         // blasterConnection.getOrb().run(); // Usually your client won't exit after this, uncomment the run() method
      }
      catch(XmlBlasterException e) {
         Log.error(ME, "XmlBlasterException: " + e.reason);
      }
      catch (Exception e) {
         Log.error(ME, "Client failed: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQoS)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      Log.error(ME, "Received unexpected asynchronous callback-update from xmlBlaster from publisher " + updateQoS.getSender() + ":");
      return "";
   }

   /**
    * Initialize command line argument handling (this is optional)
    */
   private void initArgs(String args[])
   {
      boolean showUsage = false;
      try {
         showUsage = XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         showUsage = true;
         Log.error(ME, e.toString());
      }
      if (showUsage) {
         Log.plain("\nAvailable options:");
         Log.plain("   -name               The login name [ClientSub].");
         Log.plain("   -passwd             The Password [secret].");
         XmlBlasterConnection.usage();
         Log.usage();
         Log.exit(ME, "Example: jaco javaclients.ClientSub -name Jeff\n");
      }
   }

   public static void main(String args[])
   {
      new ClientSubDispatch(args);
      Log.exit(ClientSubDispatch.ME, "Good bye");
   }
} // ClientSubDispatch

