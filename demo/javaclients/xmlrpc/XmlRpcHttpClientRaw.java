/*------------------------------------------------------------------------------
Name:      XmlRpcHttpClientRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to post a xml-rpc message thru the HTTP protocol
Version:   $Id$
Author:    "Michele Laghi" <laghi@swissinfo.org>
------------------------------------------------------------------------------*/

package javaclients.xmlrpc;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.xmlrpc.*;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.util.MsgUnit;

/**
 * Raw demo showing how to implement a client which connects to xmlBlaster via
 * xml-rpc.
 * <br />
 * This is a demo showing what happens 'low level', used with 'demo.xml',
 * sending the method calls directly in xml style.
 * <br />
 * You must be careful when sending xml strings.
 * The "<" character inside these strings must be
 * converted to "&lt;", otherwise the xmlrpc parser will erroneously try to
 * parse xmlBlaster specific stuff, resulting in a parsing error (see demo.xml
 * on this directory).
 * <p/>
 * This demo first reads "raw" procedure calls from the standard input.
 *
 * @author "Michele Laghi" <laghi@swissinfo.org>
 * @see org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection
 */
public class XmlRpcHttpClientRaw
{
   private static final String ME = "XmlRpcHttpClientRaw";
   private final Global glob;
   private static Logger log = Logger.getLogger(XmlRpcHttpClientRaw.class.getName());

   /**
    * Constructor.
    */
   public XmlRpcHttpClientRaw (Global glob)
   {
      this.glob = glob;

   }


   /**
    * executes a 'raw' xml-rpc method call through the HTTP protocol.
    * @param inputString the xml-rpc string to execute (xml literal)
    * @return a string containing the result of the remote procedure call.
    */
   public String execute(String urlStr, String inputString) throws XmlBlasterException
   {
      StringBuffer ret = null;
      try {
         // connect to the server
         URL url = new URL(urlStr);
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
         log.severe(ex1.toString());
         throw new XmlBlasterException(ME, ex1.toString());
      }
      catch (IOException ex2) {
         log.severe(ex2.toString());
         throw new XmlBlasterException(ME, ex2.toString());
      }

      return ret.toString();
   }


   /**
    * Invokes the methods as described in demo.xml
    */
   private void testRaw()
   {
      try {
         String host = glob.getProperty().get("dispatch/connection/plugin/xmlrpc/hostname", "localhost");
         int port = glob.getProperty().get("dispatch/connection/plugin/xmlrpc/port", 8080);
         int cb_port = glob.getProperty().get("dispatch/callback/plugin/xmlrpc/port", 8081);
         String urlStr = "http://" + host + ":" + port;

         log.info("Connected to xmlBlaster using XMLRPC");

         // reads from the standard input stream. Ignores the lines until the
         // first comment line containing the word COMMAND

         BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
         String line = "";

         String cmd = "";
         boolean hasStarted = false;
         log.info("Processing data from stdin ...");
         while ( (line = reader.readLine()) != null) {
            if (line.indexOf("COMMAND") == -1) {
               if (hasStarted) cmd += line + "\n";
            }

            else {
               if (cmd.length() > 0) {
                  System.out.println("THE COMMAND IS: " + cmd + "\nEND OF COMMAND");
                  System.out.println(execute(urlStr, cmd));
               }
               cmd = "";
               hasStarted = true;
            }

         }
      }
      catch (XmlBlasterException ex) {
         log.severe("exception: " + ex);
      }
      catch (IOException ex1) {
         log.severe("exception:"  + ex1);
      }
   }


   /**
    * Only for testing purposes.
    * <pre>
    * java javaclients.xmlrpc.XmlRpcHttpClientRaw < demo.xml -trace true
    * </pre>
    */
   public static void main (String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         usage();
         System.exit(1);
      }
      XmlRpcHttpClientRaw client = new XmlRpcHttpClientRaw(glob);
      client.testRaw();
   }


   /**
    * Command line usage.
    */
   private static void usage()
   {
      System.out.println("----------------------------------------------------------");
      System.out.println("java javaclients.xmlrpc.XmlRpcHttpClientRaw < demo.xml <options>");
      System.out.println("----------------------------------------------------------");
      System.out.println("   -h                  Show the complete usage.");
      System.out.println("   -dispatch/connection/plugin/xmlrpc/hostname  The XMLRPC web server host [localhost].");
      System.out.println("   -dispatch/connection/plugin/xmlrpc/port      The XMLRPC web server port [8080].");
      //System.out.println("   -xmlrpc.cb_host     My XMLRPC callback web server host (e.g. for multi homed hosts) [localhost].");
      //System.out.println("   -xmlrpc.cb_port     My XMLRPC callback web server port [8081].");
      System.out.println(Global.instance().usage());
      System.out.println("----------------------------------------------------------");
      System.out.println("");
   }
}


