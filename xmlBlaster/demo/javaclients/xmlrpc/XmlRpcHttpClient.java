/*------------------------------------------------------------------------------
Name:      XmlRpcHttpClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to post a xml-rpc message thru the HTTP protocol
Version:   $Id: XmlRpcHttpClient.java,v 1.5 2000/10/13 08:34:11 ruff Exp $
Author:    "Michele Laghi" <michele.laghi@attglobal.net>
------------------------------------------------------------------------------*/

package javaclients.xmlrpc;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.xmlrpc.*;
import org.xmlBlaster.client.protocol.xmlrpc.XmlBlasterProxy;

import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Demo showing how to implement a client which connects to xmlBlaster via
 * xml-rpc. There are basically two ways of sending the procedure calls:
 * <ul>
 * <li/>You can either invoke java methods which take care of the conversion
 * to xml (like methods provided by helma.xmlrpc.XmlRpcClient: encapsulated
 * here by org.xmlBlaster.client.protocol.xmlrpc.XmlBlasterProxy) or
 * <li/>you can send the method calls directly in xml style.
 * </ul> Note that if you choose the later case, you must be carefull when
 * sending xml strings. The "<" character inside these strings must be
 * converted to "&lt;", otherwise the xmlrpc parser will erroneously try to
 * parse xmlBlaster specific stuff, resulting in a parsing error (see demo.xml
 * on this directory).
 * <p/>
 * Both alternatives are shown in this demo.
 * This demo first reads "raw" procedure calls from the standard input. Once all
 * these methods have been called, some additional calls are made making through
 * the XmlBlasterProxy class. When using this class as a client, the xmlrpc
 * protocol is completely transparent.
 *
 * @author "Michele Laghi" <michele.laghi@attglobal.net>
 */
public class XmlRpcHttpClient extends XmlBlasterProxy
{
   private static final String ME = "XmlRpcHttpClient";


   /**
    * Constructor.
    * @param url url of the xmlBlaster server (its xmlrpc port). Ex:
    *           "http://localhost:8080".
    * @param callbackPort the port (on this host) on which the client will listen
    *                   for incoming updates.
    */
   public XmlRpcHttpClient (String url, int callbackPort) throws XmlBlasterException
   {
      super(url, callbackPort);
   }


   /**
    * executes a xml-rpc method call through the HTTP protocol.
    * @param inputString the xml-rpc string to execute (xml literal)
    * @return a string containing the result of the remote procedure call.
    */
   public String execute (String inputString) throws XmlBlasterException
   {
      StringBuffer ret = null;
      try {
         // connect to the server
         URL url = new URL(super.url);
         HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();

         // send a POST
         urlConnection.setRequestMethod("POST");
         urlConnection.setDoOutput(true);
         OutputStream outStream = urlConnection.getOutputStream();
         byte[] conversionHelper = inputString.getBytes();
         outStream.write(conversionHelper);
         outStream.flush();

         // read the answer of the server and store it in a string
         ret = new StringBuffer();
         InputStream inStream = urlConnection.getInputStream();
         int bytes = 0, deltaBytes = 0;
         int maxBytes = urlConnection.getContentLength();

         //         while ( (bytes = inStream.available()) > 0) {
         while (bytes < maxBytes) {
            deltaBytes = inStream.available();
            byte buffer[] = new byte[deltaBytes];
            inStream.read(buffer);
            bytes += deltaBytes;
            ret.append(new String(buffer));
         }
      }

      catch (MalformedURLException ex1) {
         Log.error(ME, ex1.toString());
         throw new XmlBlasterException(ME, ex1.toString());
      }
      catch (IOException ex2) {
         Log.error(ME, ex2.toString());
         throw new XmlBlasterException(ME, ex2.toString());
      }

      return ret.toString();
   }


   /**
    * Only for testing purposes.
    * <pre>
    * java javaclients.xmlrpc.XmlRpcHttpClient < demo.xml -trace true
    * </pre>
    */
   public static void main (String args[])
   {
      final String ME = "XmlRpcHttpClient";
      boolean showUsage = false;

      try {
         if (XmlBlasterProperty.init(args)) {
            usage();
            Log.exit(ME, "");
         }
      } catch(org.jutils.JUtilsException e) {
         usage();
         Log.panic(ME, e.toString());
      }

      try {
         String host = XmlBlasterProperty.get("xmlrpc.host", "localhost");
         int port = XmlBlasterProperty.get("xmlrpc.port", 8080);
         int cb_port = XmlBlasterProperty.get("xmlrpc.cb_port", 8081);
         XmlRpcHttpClient client = new XmlRpcHttpClient("http://" + host + ":" + port, cb_port);

         Log.info(ME, "Connected to xmlBlaster using XML-RPC");

         // reads from the standard input stream. Ignores the lines until the
         // first comment line containing the word COMMAND

         BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
         String line = "";

         String cmd = "";
         boolean hasStarted = false;
         Log.info(ME, "Processing data from stdin ...");
         while ( (line = reader.readLine()) != null) {
            if (line.indexOf("COMMAND") == -1) {
               if (hasStarted) cmd += line + "\n";
            }

            else {
               if (cmd.length() > 0) {
                  System.out.println("THE COMMAND IS: " + cmd + "\nEND OF COMMAND");
                  System.out.println(client.execute(cmd));
               }
               cmd = "";
               hasStarted = true;
            }

         }

         // and here come the methods to invoke if you want to use the xml-rpc
         // protocol transparently ...
         Log.info(ME, "Going to invoke xmlBlaster using XmlRpc-XmlBlasterProxy");

         String qos = "<qos><callback type='XML-RPC'>http://" + getLocalIP() + ":" + cb_port + "</callback></qos>";
         String sessionId = "Session1";

         String loginAnswer = client.login("LunaMia", "silence", qos, sessionId);
         Log.info(ME, "Login successful, the answer from the login is: " + loginAnswer);

         String contentString = "This is a simple Test Message for the xml-rpc Protocol";
         byte[] content = contentString.getBytes();

         PublishKeyWrapper xmlKey = new PublishKeyWrapper("", "text/xml", null);

         MessageUnit msgUnit = new MessageUnit(xmlKey.toXml(), content, "<qos></qos>");
         String publishOid = client.publish(sessionId, msgUnit);
         System.err.println("Published message with " + publishOid);

         SubscribeKeyWrapper subscribeKey = new SubscribeKeyWrapper(publishOid);

         System.err.println("Subscribe key: " + subscribeKey.toXml());

         client.subscribe(sessionId, subscribeKey.toXml(), "");

         // wait some time if necessary ....
         client.erase(sessionId, subscribeKey.toXml(), "");

         //
         System.err.println("EXIT NOW....");
         System.exit(0);

      }
      catch (XmlBlasterException ex) {
         System.err.println("exception: " + ex);
      }

      catch (IOException ex1) {
         System.err.println("exception:"  + ex1);
      }

   }

   /**
    * You can specify the local IP address with e.g. -xmlrpc.cb_host 192.168.10.12
    * on command line.
    * @return The local IP address, defaults to '127.0.0.1' if not known.
    */
   public static String getLocalIP()
   {
      String ip_addr = XmlBlasterProperty.get("xmlrpc.cb_host", (String)null);
      if (ip_addr == null) {
         try {
            ip_addr = java.net.InetAddress.getLocalHost().getHostAddress(); // e.g. "202.100.100.12"
         } catch (java.net.UnknownHostException e) {
            Log.warn(ME, "Can't determine local IP address, try e.g. '-xmlrpc.cb_host 192.168.10.12' on command line: " + e.toString());
         }
         if (ip_addr == null) ip_addr = "127.0.0.1";
      }
      return ip_addr;
   }



   /**
    * Command line usage.
    */
   private static void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "java -Dsax.driver=com.sun.xml.parser.Parser javaclients.xmlrpc.XmlRpcHttpClient < demo.xml <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "   -h                  Show the complete usage.");
      Log.plain(ME, "   -xmlrpc.host        The XML-RPC web server host [localhost].");
      Log.plain(ME, "   -xmlrpc.port        The XML-RPC web server port [8080].");
      Log.plain(ME, "   -xmlrpc.cb_host     My XML-RPC callback web server host (e.g. for multi homed hosts) [localhost].");
      Log.plain(ME, "   -xmlrpc.cb_port     My XML-RPC callback web server port [8081].");
      Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "");
   }
}


