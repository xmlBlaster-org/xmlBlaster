/*-----------------------------------------------------------------------------
Name:      HttpIorForCpp.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Writes to standard output the IOR read from the HTTP Server 
Version:   $Id: HttpIorForCpp.java,v 1.1 2000/07/14 02:11:02 laghi Exp $
Author:    michele.laghi@attglobal.net
-----------------------------------------------------------------------------*/

package org.xmlBlaster.util.cpp;

import org.jutils.JUtilsException;
import org.jutils.init.Property;
import org.jutils.log.Log;

/**
 * Accesses the http server, reads the IOR for the AuthServer and prints it 
 * on the standard output.
 */

public class HttpIorForCpp 
{

   private Property properties_ = null;

   /**
    * Public constructor
    */
   public HttpIorForCpp (String args[]) 
   {
      try {
         properties_ = new Property("xmlBlaster.properties", true, args, 
	                            true);
      }
      catch (JUtilsException e) {
      }
      
      Log.setLogLevel(0);
   }
    
   /**
    * Gets the authentication server (reads via the http protocol) from the
    * given iorHost and iorPort.
    * @param iorHost string of the url of the server on which the xmlBlaster
    *                server is running. (ex: "www.xmlBlaster.org").
    * @param iorPort an integer specifying the port on which it will knock.
    * @return        the IOR string of the authServer.
    */
   private String getAuthenticationServiceIOR (String iorHost, int iorPort) 
          throws Exception
   {
      java.net.URL nsURL = new java.net.URL("http", iorHost, iorPort, 
                                            "/AuthenticationService.ior");
	
      java.io.InputStream nsis = nsURL.openStream();
      byte[] bytes = new byte[4096];
      java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
      int numbytes;
      while (nsis.available() > 0 && (numbytes = nsis.read(bytes)) > 0) {
         bos.write(bytes, 0, numbytes);
      }
      nsis.close();
      String ior = bos.toString();
      if (!ior.startsWith("IOR:"))
         ior = "IOR:000" + ior; 
	 // hack for JDK 1.1.x, where the IOR: is cut away from ByteReader ??? !!!
      return ior;
   }
    
   /**
    * reads the IOR from the host and port specified either in the 
    * xmlBlaster.properties file or in the argument list (passed in the
    * constructor).
    *
    * @return        the IOR string of the authServer.
    */
   public String getIOR ()
   {
      String iorHost = properties_.get("iorHost", "localhost");
      int    iorPort = 
      properties_.get("iorPort", org.xmlBlaster.protocol.corba.
                      CorbaDriver.DEFAULT_HTTP_PORT); // 7609
      String authServerIOR = null;
      if (iorHost != null && iorPort > 0) {
         try {
	    authServerIOR = getAuthenticationServiceIOR(iorHost, iorPort);
         } 
	 catch (java.lang.Exception ex) {
	    System.err.println("Error in retrieving the IOR" + ex);
	 }
      }
      return authServerIOR;
   }

	
   public static void main (String args[]) 
   {
      java.io.PrintStream oldStream = System.out;
      System.setOut(System.err);
      HttpIorForCpp httpReader = new HttpIorForCpp(args);
      System.setOut(oldStream);
      System.out.println(httpReader.getIOR());
   }
}
