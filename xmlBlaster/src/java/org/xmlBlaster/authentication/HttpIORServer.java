/*------------------------------------------------------------------------------
Name:      HttpIORServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Delivering the Authentication Service IOR over HTTP
Version:   $Id: HttpIORServer.java,v 1.5 2000/02/28 18:39:50 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import java.util.*;
import java.net.*;
import java.io.*;


/**
 * Delivering the Authentication Service IOR over HTTP.
 * <p />
 * This little HTTP server is always running in the xmlBlaster on the
 * default port 7609.<br />
 * Clients may access through this port the AuthServer IOR if they
 * don't want to use a naming service
 *
 * @version $Revision: 1.5 $
 * @author $Author: ruff $
 */
public class HttpIORServer extends Thread
{
   private String ME = "HttpIORServer";
   private final int HTTP_PORT;
   private String ior = null;
   private ServerSocket listen = null;
   private boolean running = true;


   /**
    * Create this instance when a client did a login.
    * <p />
    * @param authInfo the AuthenticationInfo with the login informations for this client
    */
   public HttpIORServer(int port, String ior)
   {
      this.ior = ior;
      this.HTTP_PORT = port;
      if (Log.CALLS) Log.calls(ME, "Creating new HttpIORServer");
      start();
   }


   /**
    */
   public void run()
   {
      try {
         listen = new ServerSocket(HTTP_PORT);
         while (running) {
            Socket accept = listen.accept();
            if (!running) {
               Log.info(ME, "Closing http server port");
               return;
            }
            HandleRequest hh = new HandleRequest(accept, ior);
         }
      }
      catch (java.net.BindException e) {
         Log.error(ME, "HTTP server problem: " + e.toString());
      }
      catch (java.net.SocketException e) {
         Log.info(ME, "Socket closed successfully: " + e.toString());
      }
      catch (IOException e) {
         Log.error(ME, "HTTP server problem: " + e.toString());
      }
   }


   /**
    * Close the listener port
    */
   public void shutdown() throws IOException
   {
      running = false;
      listen.close();
   }
}


/**
 * Handles a request from a client, delivering the AuthServer IOR
 */
class HandleRequest extends Thread
{
   private String ME = "HandleRequest";
   private final Socket sock;
   private final String ior;


   /**
    */
   public HandleRequest(Socket sock, String iorStr)
   {
      this.sock = sock;
      this.ior = iorStr;
      start();
   }

   /**
    */
   public void run()
   {
      if (Log.CALLS) Log.calls(ME, "Handling client request, accessing AuthServer IOR ...");
      try {
         BufferedReader iStream = new BufferedReader(new InputStreamReader(sock.getInputStream()));
         DataOutputStream oStream = new DataOutputStream(sock.getOutputStream());
         String clientRequest = iStream.readLine();
         if (Log.TRACE && clientRequest != null)
            Log.trace(ME, "Ignoring data from client request: '" + clientRequest + "'");
         // Log.trace(ME, "Sending IOR='" + ior + "'");
         oStream.write(ior.getBytes()); // This cuts away the IOR: string under JDK 1.1.x ??!!!
         iStream.close();
         oStream.close();
         sock.close();
      }
      catch (IOException e) {
         Log.error(ME, "Problems with sending IOR to client: " + e.toString());
         // throw new XmlBlasterException(ME, "Problems with sending IOR to client: " + e.toString());
      }
   }
}
