/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientQuery.java,v 1.18 2002/05/01 21:39:51 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
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
      Global glob = new Global();
      if (glob.init(args) != 0) usage("Aborted");

      try {
         String loginName = Args.getArg(args, "-name", ME); // check if parameter -name <userName> is given at startup of client
         String passwd = Args.getArg(args, "-passwd", "secret");

         queryString = Args.getArg(args, "-queryXpath", (String)null);
         if (queryString != null)
            queryType = "XPATH";
         else
            usage("Please enter a query string");

         XmlBlasterConnection con = new XmlBlasterConnection(args);
         con.login(loginName, passwd, null);


         String xmlKey = "<key oid='' queryType='" + queryType + "'>\n" +
                            queryString +
                         "</key>";
         MessageUnit[] msgArr = null;
         try {
            msgArr = con.get(xmlKey, "<qos></qos>");
            Log.info(ME, "Got " + msgArr.length + " messages for query '" + queryString + "':");
            for (int ii=0; ii<msgArr.length; ii++) {
               UpdateKey updateKey = new UpdateKey(glob, msgArr[ii].xmlKey);
               Log.info("UpdateKey", "\n" + updateKey.toXml());
               Log.info("content", "\n" + new String(msgArr[ii].content) + "\n");
            }
         } catch(XmlBlasterException e) {
            Log.error(ME, "XmlBlasterException: " + e.reason);
         }

         con.logout();
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
      XmlBlasterConnection.usage();
      Log.usage();
      Log.plain("Example: jaco javaclients.ClientQuery -queryXpath //key\n");
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
