/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientQuery.java,v 1.3 2000/01/19 21:03:48 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;


/**
 * This client allows you to query xmlBlaster with method get().
 * <p>
 * It doesn't implement a Callback server, since it only access xmlBlaster
 * using the synchronous get() method.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco javaclients.ClientQuery -queryXpath "//AGENT/DRIVER"
 * </pre>
 */
public class ClientQuery
{
   private Server xmlBlaster = null;
   private static String ME = "Heidi";
   private String queryString;
   private String queryType = "XPATH";

   public ClientQuery(String args[])
   {
      try {
         ME = Args.getArg(args, "-name", ME); // check if parameter -name <userName> is given at startup of client
         String loginName = ME;

         queryString = Args.getArg(args, "-queryXpath", (String)null);
         if (queryString != null)
            queryType = "XPATH";
         else
            Log.panic(ME, "Please enter a query string, example:\n" +
                          "   jaco javaclients.ClientQuery -queryXpath \"//DRIVER/AGENT\"");

         CorbaConnection corbaConnection = new CorbaConnection(args);
         String qos = "";
         String passwd = "some";
         xmlBlaster = corbaConnection.login(loginName, passwd, (BlasterCallback)null, qos);


         String xmlKey = "<key oid='' queryType='" + queryType + "'>\n" +
                            queryString +
                         "</key>";
         MessageUnitContainer[] msgArr = null;
         try {
            msgArr = xmlBlaster.get(xmlKey, qos);
            Log.info(ME, "Got " + msgArr.length + " messages for query '" + queryString + "':");
            for (int ii=0; ii<msgArr.length; ii++) {
               UpdateKey updateKey = new UpdateKey();
               updateKey.init(msgArr[ii].messageUnit.xmlKey);
               Log.dump("UpdateKey", "\n" + updateKey.printOn().toString());
               Log.dump("content", "\n" + new String(msgArr[ii].messageUnit.content) + "\n");
            }
         } catch(XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }

         corbaConnection.logout(xmlBlaster);
      }
      catch (XmlBlasterException e) {
          Log.error(ME, "Error occurred: " + e.toString());
          e.printStackTrace();
      }
   }


   /**
    */
   public static void main(String args[])
   {
      new ClientQuery(args);
      Log.exit(ClientQuery.ME, "Good bye");
   }
}
