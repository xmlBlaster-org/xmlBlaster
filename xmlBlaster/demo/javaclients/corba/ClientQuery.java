/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientQuery.java,v 1.2 2000/06/26 06:40:28 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.corba;

import org.jutils.log.Log;
import org.jutils.init.Args;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This client allows you to query xmlBlaster synchronous with method get().
 * <p>
 * It doesn't implement a Callback server, since it only access xmlBlaster
 * using the synchronous get() method.
 * <p>
 * Invoke example:<br />
 * <pre>
 *    jaco javaclients.ClientQuery -queryXpath "//key"
 * </pre>
 */
public class ClientQuery
{
   private static String ME = "ClientQuery";
   private String queryString;
   private String queryType = "XPATH";

   public ClientQuery(String args[])
   {
      // Initialize command line argument handling (this is optional)
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         usage(e.toString());
      }

      try {
         String loginName = Args.getArg(args, "-name", ME); // check if parameter -name <userName> is given at startup of client
         String passwd = Args.getArg(args, "-passwd", "secret");

         queryString = Args.getArg(args, "-queryXpath", (String)null);
         if (queryString != null)
            queryType = "XPATH";
         else
            usage("Please enter a query string");

         CorbaConnection corbaConnection = new CorbaConnection(args);
         corbaConnection.login(loginName, passwd, null);


         String xmlKey = "<key oid='' queryType='" + queryType + "'>\n" +
                            queryString +
                         "</key>";
         MessageUnit[] msgArr = null;
         try {
            msgArr = corbaConnection.get(xmlKey, "<qos></qos>");
            Log.info(ME, "Got " + msgArr.length + " messages for query '" + queryString + "':");
            for (int ii=0; ii<msgArr.length; ii++) {
               UpdateKey updateKey = new UpdateKey();
               updateKey.init(msgArr[ii].xmlKey);
               Log.info("UpdateKey", "\n" + updateKey.printOn().toString());
               Log.info("content", "\n" + new String(msgArr[ii].content) + "\n");
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

   private void usage(String text)
   {
      Log.plain("\nAvailable options:");
      Log.plain("   -name               The login name [ClientQuery].");
      Log.plain("   -passwd             The password [secret].");
      Log.plain("   -queryXpath         \"//key\"");
      CorbaConnection.usage();
      Log.usage();
      Log.plain("Example: jaco javaclients.corba.ClientQuery -queryXpath //key\n");
      Log.panic(ME, text);
   }

   /**
    */
   public static void main(String args[])
   {
      new ClientQuery(args);
      Log.exit(ClientQuery.ME, "Good bye");
   }
}
