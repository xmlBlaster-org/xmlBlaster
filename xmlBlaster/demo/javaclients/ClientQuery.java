/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: ClientQuery.java,v 1.25 2003/03/24 16:12:45 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients;

import org.jutils.log.LogChannel;
import org.jutils.init.Args;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.util.MsgUnit;


/**
 * This client allows you to query xmlBlaster synchronous with method get().
 * <p>
 * It doesn't implement a Callback server, since it only access xmlBlaster
 * using the synchronous get() method.
 * <p>
 * Invoke example:<br />
 * <pre>
 *    java javaclients.ClientQuery -queryXpath "//key"
 * </pre>
 */
public class ClientQuery
{
   private static String ME = "ClientQuery";
   private final LogChannel log;
   private String queryString;
   private String queryType = "XPATH";

   public ClientQuery(String args[])
   {
      // Initialize command line argument handling (this is optional)
      Global glob = new Global();
      log = glob.getLog(null);
      if (glob.init(args) != 0) usage("Aborted");

      try {
         String loginName = Args.getArg(args, "-session.name", ME); // check if parameter -session.name <userName> is given at startup of client
         String passwd = Args.getArg(args, "-passwd", "secret");

         queryString = Args.getArg(args, "-queryXpath", (String)null);
         if (queryString != null)
            queryType = "XPATH";
         else
            usage("Please enter a query string");

         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();
         con.connect(null, null);


         String xmlKey = "<key oid='' queryType='" + queryType + "'>\n" +
                            queryString +
                         "</key>";
         MsgUnit[] msgArr = null;
         try {
            msgArr = con.get(xmlKey, "<qos></qos>");
            log.info(ME, "Got " + msgArr.length + " messages for query '" + queryString + "':");
            for (int ii=0; ii<msgArr.length; ii++) {
               UpdateKey updateKey = new UpdateKey(glob, msgArr[ii].getKey());
               log.info("UpdateKey", "\n" + updateKey.toXml());
               log.info("content", "\n" + new String(msgArr[ii].getContent()) + "\n");
            }
         } catch(XmlBlasterException e) {
            log.error(ME, "XmlBlasterException: " + e.getMessage());
         }

         con.disconnect(null);
      }
      catch (org.jutils.JUtilsException e) {
          log.error(ME, "Error occurred: " + e.toString());
          e.printStackTrace();
      }
      catch (XmlBlasterException e) {
          log.error(ME, "Error occurred: " + e.toString());
          e.printStackTrace();
      }
   }

   private void usage(String text)
   {
      log.plain(ME, "\nAvailable options:");
      log.plain(ME, "   -queryXpath         \"//key\"");
      System.out.println(Global.instance().usage());
      log.plain(ME, "Example: java javaclients.ClientQuery -queryXpath //key\n");
      log.plain(ME, text);
      System.exit(1);
   }

   public static void main(String args[]) {
      new ClientQuery(args);
   }
}
