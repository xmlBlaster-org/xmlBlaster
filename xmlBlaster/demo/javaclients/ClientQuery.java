/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientQuery.java,v 1.12 2000/06/19 15:48:35 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.jutils.log.Log;
import org.jutils.init.Args;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;


/**
 * This client allows you to query xmlBlaster with method get().
 * <p>
 * It doesn't implement a Callback server, since it only access xmlBlaster
 * using the synchronous get() method.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco javaclients.ClientQuery -queryXpath "//key"
 * </pre>
 */
public class ClientQuery
{
   private static String ME = "Heidi";
   private String queryString;
   private String queryType = "XPATH";

   public ClientQuery(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      Log.setLogLevel(XmlBlasterProperty.getProperty());
      try {
         ME = Args.getArg(args, "-name", ME); // check if parameter -name <userName> is given at startup of client
         String loginName = ME;

         queryString = Args.getArg(args, "-queryXpath", (String)null);
         if (queryString != null)
            queryType = "XPATH";
         else
            Log.panic(ME, "Please enter a query string, example:\n" +
                          "   jaco javaclients.ClientQuery -queryXpath \"//key\"");

         CorbaConnection corbaConnection = new CorbaConnection(args);
         String passwd = "some";
         corbaConnection.login(loginName, passwd, null);


         String xmlKey = "<key oid='' queryType='" + queryType + "'>\n" +
                            queryString +
                         "</key>";
         MessageUnitContainer[] msgArr = null;
         try {
            msgArr = corbaConnection.get(xmlKey, "<qos></qos>");
            Log.info(ME, "Got " + msgArr.length + " messages for query '" + queryString + "':");
            for (int ii=0; ii<msgArr.length; ii++) {
               UpdateKey updateKey = new UpdateKey();
               updateKey.init(msgArr[ii].msgUnit.xmlKey);
               Log.info("UpdateKey", "\n" + updateKey.printOn().toString());
               Log.info("content", "\n" + new String(msgArr[ii].msgUnit.content) + "\n");
            }
         } catch(XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }

         corbaConnection.logout();
      }
      catch (org.jutils.JUtilsException e) {
          Log.error(ME, "Error occurred: " + e.toString());
          e.printStackTrace();
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
