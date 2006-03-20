/*------------------------------------------------------------------------------
Name:      ClientGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients;

import java.util.logging.Logger;

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
   private static Logger log = Logger.getLogger(ClientQuery.class.getName());
   private String queryString;
   private String queryType = "XPATH";

   public ClientQuery(String args[])
   {
      // Initialize command line argument handling (this is optional)
      Global glob = new Global();

      if (glob.init(args) != 0) usage("Aborted");

      try {
         String loginName = glob.getProperty().get("session.name", ME); // check if parameter -session.name <userName> is given at startup of client
         String passwd = glob.getProperty().get("passwd", "secret");

         queryString = glob.getProperty().get("queryXpath", (String)null);
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
            log.info("Got " + msgArr.length + " messages for query '" + queryString + "':");
            for (int ii=0; ii<msgArr.length; ii++) {
               UpdateKey updateKey = new UpdateKey(glob, msgArr[ii].getKey());
               log.info("\n" + updateKey.toXml());
               log.info("\n" + new String(msgArr[ii].getContent()) + "\n");
            }
         } catch(XmlBlasterException e) {
            log.severe("XmlBlasterException: " + e.getMessage());
         }

         con.disconnect(null);
      }
      catch (XmlBlasterException e) {
          log.severe("Error occurred: " + e.toString());
          e.printStackTrace();
      }
   }

   private void usage(String text)
   {
      System.out.println("\nAvailable options:");
      System.out.println("   -queryXpath         \"//key\"");
      System.out.println(Global.instance().usage());
      System.out.println("Example: java javaclients.ClientQuery -queryXpath //key\n");
      System.out.println(text);
      System.exit(1);
   }

   public static void main(String args[]) {
      new ClientQuery(args);
   }
}
