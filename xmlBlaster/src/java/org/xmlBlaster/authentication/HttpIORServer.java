/*------------------------------------------------------------------------------
Name:      HttpIORServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Delivering the Authentication Service IOR over HTTP
Version:   $Id: HttpIORServer.java,v 1.1 1999/12/08 12:16:17 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import java.util.*;
import java.net.*;
import java.io.*;


/**
 * Delivering the Authentication Service IOR over HTTP
 * <p />
 *
 * @version $Revision: 1.1 $
 * @author $Author: ruff $
 */
public class HttpIORServer extends Thread
{
   private String ME = "HttpIORServer";
   private final int HTTP_PORT;
   private String ior = null;


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
         ServerSocket listen = new ServerSocket(HTTP_PORT);
         while (true) {
            HandleRequest hh = new HandleRequest(listen.accept(), ior);
         }
      }
      catch (IOException e) {
         Log.error(ME, e.toString());
      }
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
   public HandleRequest(Socket sock, String ior)
   {
      this.sock = sock;
      this.ior = ior;
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
         oStream.write(ior.getBytes());
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
