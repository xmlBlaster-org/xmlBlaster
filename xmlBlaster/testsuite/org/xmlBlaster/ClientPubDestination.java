/*------------------------------------------------------------------------------
Name:      ClientPubDestination.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster and publishing to destinations
Version:   $Id: ClientPubDestination.java,v 1.4 1999/12/10 16:44:45 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;


/**
 * This client tests the PtP style, Manuel sends to Ulrike a love letter. 
 * <p>
 * Note that the two clients (client logins) are simulated in this class.<br />
 * Manuel is the 'sender' and Ulrike the 'receiver'
 * <p>
 * Invoke example:
 *    ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientPubDestination
 */
public class ClientPubDestination
{
   private Server xmlBlaster = null;
   private final static String ME = "ClientPubDestination";
   private final String[] args;

   private final String senderName = "Manuel";
   private String publishOid = "";

   private final String receiverName = "Ulrike";
   private CorbaConnection receiverConnection = null;
   private Server receiverXmlBlaster = null;
   private int numReceived = 0;


   /**
    */
   public ClientPubDestination(String args[])
   {
      this.args = args;
   }


   /**
    * @return true: No errors, false: panic
    */
   private boolean testScenario()
   {
      if (initReceiver(receiverName) == false) return false;

      if (initSender(senderName) == false) return false;

      receiverConnection.logout(receiverXmlBlaster);

      return true;
   }


   /**
    * @return true: No errors, false: panic
    */
   private boolean initReceiver(String name)
   {
      boolean retVal = true;
      String loginName = ME;
      if (name != null) loginName = name;

      try {
         receiverConnection = new CorbaConnection(args);

         //---------- Building a Callback server ----------------------
         org.omg.PortableServer.POA poa = org.omg.PortableServer.POAHelper.narrow(receiverConnection.getOrb().resolve_initial_references("RootPOA"));
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new PubDestinationCallback(loginName, this));
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));
         Log.trace(loginName, "Exported Callback Server interface");

         String passwd = "some";
         receiverXmlBlaster = receiverConnection.login(loginName, passwd, callback, "<qos></qos>");
      }
      catch (Exception e) {
          e.printStackTrace();
          Log.error(ME, e.toString());
          retVal = false;
      }
      return retVal;
   }


   /**
    * @return true: No errors, false: panic
    */
   private boolean initSender(String name)
   {
      boolean retVal = true;
      String loginName = ME;
      if (name != null) loginName = name;

      try {
         CorbaConnection corbaConnection = new CorbaConnection(args);

         //---------- Building a Callback server ----------------------
         org.omg.PortableServer.POA poa = org.omg.PortableServer.POAHelper.narrow(corbaConnection.getOrb().resolve_initial_references("RootPOA"));
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(new PubDestinationCallback(loginName, this));
         BlasterCallback callback = BlasterCallbackHelper.narrow(poa.servant_to_reference( callbackTie ));
         Log.trace(loginName, "Exported Callback Server interface");


         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, callback, "<qos></qos>");


         //----------- Construct a love message and send it to Ulrike ---------
         {
            String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                            "</key>";

            String qos = "<qos>" +
                         "   <destination queryType='EXACT'>" +
                                receiverName +
                         "   </destination>" +
                         "</qos>";

            String content = "Hi " + receiverName + ", i love you, " + senderName;
            MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
            try {
               publishOid = xmlBlaster.publish(messageUnit, qos);
               Log.info(ME, "Sending done, returned oid=" + publishOid);
            } catch(XmlBlasterException e) {
               Log.error(ME, "publish() XmlBlasterException: " + e.reason);
               retVal = false;
            }
         }

         Util.delay(1000);

         if (numReceived == 1)
            Log.info(ME, "Success, got one PtP message");
         else
            Log.error(ME, numReceived + " callbacks arrived, did expect one after a direct addressing from " + senderName);
         numReceived = 0;

         corbaConnection.logout(xmlBlaster);
      }
      catch (Exception e) {
          e.printStackTrace();
          Log.error(ME, e.toString());
          retVal = false;
      }
      return retVal;
   }


   /**
    * The SubCallback.update calls this method, to allow some error checking
    * @param name of client installed this Callback
    */
   public void update(String loginName, MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      if (Log.CALLS) Log.calls(loginName + "UpdateKey", "Receiving update");
      if (messageUnitArr.length != 0)
         numReceived += messageUnitArr.length;
      else
         numReceived = -1;       // error


      for (int ii=0; ii<messageUnitArr.length; ii++) {
         MessageUnit messageUnit = messageUnitArr[ii];
         UpdateKey updateKey = null;
         UpdateQoS updateQoS = null;
         byte[] content = messageUnit.content;
         try {
            updateKey = new UpdateKey(messageUnit.xmlKey);
            updateQoS = new UpdateQoS(qos_literal_Arr[ii]);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }

         // Now we know all about the received message, dump it and do some checks

         if (Log.DUMP) Log.dump(loginName + "UpdateKey", updateKey.printOn().toString());
         if (Log.DUMP) Log.dump(loginName + "content", (new String(content)).toString());
         if (Log.DUMP) Log.dump(loginName + "UpdateQoS", updateQoS.printOn().toString());

         if (loginName.equals(receiverName))
            Log.info(ME, "Success, " + loginName + " received message from " + updateQoS.getSender());
         else
            Log.error(ME, "Wrong receiver " + loginName + " expected " + receiverName);

         String keyOid = "";
         try { keyOid = updateKey.getUniqueKey(); } catch(XmlBlasterException e) { e.printStackTrace(); }

         if (!keyOid.equals(publishOid))
            Log.error(ME, "Wrong oid of message returned, publish oid = " + publishOid + " and received oid = " + keyOid);

      }
   }


   /**
    * Invoke: ${JacORB_HOME}/bin/jaco testsuite.org.xmlBlaster.ClientPubDestination
    */
   public static void main(String args[])
   {
      ClientPubDestination cl = new ClientPubDestination(args);

      if (cl.testScenario() == true)
         Log.exit(ClientPubDestination.ME, "Good bye");
      else
         Log.panic(ClientPubDestination.ME, "Good bye");
   }
} // ClientPubDestination


/**
 * Example for a callback implementation.
 */
class PubDestinationCallback implements BlasterCallbackOperations
{
   private final String ME;
   private final ClientPubDestination boss;
   private final String loginName;

   /**
    * Construct a persistently named object.
    */
   public PubDestinationCallback(java.lang.String name, ClientPubDestination boss)
   {
      this.ME = "PubDestinationCallback-" + name;
      this.boss = boss;
      this.loginName = name;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    */
   public void update(MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      boss.update(loginName, messageUnitArr, qos_literal_Arr); // Call my boss, so she can check for errors
   }
} // PubDestinationCallback

