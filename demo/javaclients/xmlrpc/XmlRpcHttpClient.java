/*------------------------------------------------------------------------------
Name:      XmlRpcHttpClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to post a xml-rpc message thru the HTTP protocol
Version:   $Id$
Author:    Michele Laghi (laghi@swissinfo.org)
------------------------------------------------------------------------------*/

package javaclients.xmlrpc;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.util.MsgUnit;

/**
 * Demo showing how to implement a client which connects to xmlBlaster via
 * xml-rpc. 
 * Calls are made through the I_XmlBlasterAccess client helper class.
 *
 * When using this class as a client, the xmlrpc
 * protocol is completely transparent, even the Callback server is created for you.
 * <p />
 * Invoke example (this client forces XMLRPC as protocol hard coded):
 * <pre>
 *    java -cp lib/xmlBlaster.jar javaclients.xmlrpc.XmlRpcHttpClient
 * </pre>
 *
 * <p />
 * NOTE:  Any java client using I_XmlBlasterAccess helper class will switch
 *        to XMLRPC if the command line parameter -protocol is specified as follows:
 * <br />
 * <pre>
 *    java -cp lib/xmlBlaster.jar javaclients.ClientSub -dispatch/connection/protocol XMLRPC
 * </pre>
 *
 * @author "Michele Laghi" <laghi@swissinfo.org>
 * @see org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection
 */
public class XmlRpcHttpClient {
   private static Logger log = Logger.getLogger(XmlRpcHttpClient.class.getName());


   /**
    * Constructor.
    */
   public XmlRpcHttpClient ()
   {
   }


   /**
    * Here come the methods to invoke if you want to use the xml-rpc
    * protocol transparently ...
    * <br />
    */
   private void testWithHelperClasses(Global glob)
   {

      try {
         // force XMLRPC protocol:
         glob.getProperty().set("client.protocol", "XMLRPC");
         
         I_XmlBlasterAccess client = glob.getXmlBlasterAccess();
         
         log.info("Going to invoke xmlBlaster using XmlRpc-I_XmlBlasterAccess");
         ConnectQos connectQos = new ConnectQos(glob, "LunaMia", "silence");
         client.connect(connectQos, null);
         log.info("Connection successful");

         String contentString = "This is a simple Test Message for the xml-rpc Protocol";
         byte[] content = contentString.getBytes();

         PublishKey xmlKey = new PublishKey(glob, "", "text/plain", null);

         MsgUnit msgUnit = new MsgUnit(xmlKey.toXml(), content, "<qos><forceUpdate /></qos>");
         client.publish(msgUnit);
         log.info("Published a message");

         client.disconnect(null);
      }
      catch (XmlBlasterException ex) {
         log.severe("exception: " + ex.toString());
      }
      catch (Throwable ex1) {
         System.err.println("exception:"  + ex1);
      }
   }


   /**
    * Only for testing purposes.
    * <pre>
    * java javaclients.xmlrpc.XmlRpcHttpClient < demo.xml -logging FINE
    * </pre>
    */
   public static void main (String args[])
   {
      final String ME = "XmlRpcHttpClient";

      Global glob = new Global();
      if (glob.init(args) != 0) {
         usage();
         System.exit(1);
      }

      XmlRpcHttpClient client = new XmlRpcHttpClient();

      client.testWithHelperClasses(glob);
   }


   /**
    * Command line usage.
    */
   private static void usage()
   {
      System.out.println("----------------------------------------------------------");
      System.out.println("java javaclients.xmlrpc.XmlRpcHttpClient < demo.xml <options>");
      System.out.println("----------------------------------------------------------");
      System.out.println(Global.instance().usage());
      System.out.println("----------------------------------------------------------");
      System.out.println("");
   }
}


