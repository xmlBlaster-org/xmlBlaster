/*-----------------------------------------------------------------------------
Name:      HttpIorForCpp.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Writes to standard output the IOR read from the HTTP Server
Version:   $Id: HttpIorForCpp.java,v 1.5 2001/09/05 12:21:27 ruff Exp $
Author:    michele.laghi@attglobal.net
-----------------------------------------------------------------------------*/

package org.xmlBlaster.util.cpp;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.client.protocol.corba.CorbaConnection;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServer;
import org.xmlBlaster.protocol.corba.authenticateIdl.AuthServerHelper;


/**
 * Accesses the http server, reads the IOR for the AuthServer and prints it
 * on the standard output.
 */

public class HttpIorForCpp
{
   String authServerIOR;
   boolean showUsage = false;

   public HttpIorForCpp (String args[]) throws Exception
   {
      showUsage = XmlBlasterProperty.init(args);

      // check if parameter -name <userName> is given at startup of client
      String loginName = XmlBlasterProperty.get("-name", "ben");
      String passwd = XmlBlasterProperty.get("-passwd", "secret");

      CorbaConnection con = new CorbaConnection(args);

      AuthServer authServer = con.getAuthenticationService();
      authServerIOR = con.getOrb().object_to_string(authServer);
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
      return authServerIOR;
   }


   static void usage()
   {
      Log.plain("\nAvailable options:");
      Log.plain("   -name               The login name [ClientSub].");
      Log.plain("   -passwd             The login name [secret].");
      CorbaConnection.usage();
      Log.usage();
      Log.exit("", "Example: jaco org.xmlBlaster.util.cpp.HttpIorForCpp -name Jeff\n");
   }

   /** java  org.xmlBlaster.util.cpp.HttpIorForCpp */
   public static void main (String args[])
   {
      Exception e = null;
      class DevNull extends java.io.OutputStream { public void write(int b) {} }
      java.io.PrintStream oldOut = System.out;
      java.io.PrintStream oldErr = System.err;
      System.setOut(new java.io.PrintStream(new DevNull()));
      System.setErr(new java.io.PrintStream(new DevNull()));
      HttpIorForCpp httpReader = null;
      try {
         httpReader = new HttpIorForCpp(args);
      }
      catch (Exception ex) {
         e=ex;
      } finally {
         System.setOut(oldOut);
         System.setErr(oldErr);
      }
      if (e!=null)
         System.out.println(e.toString());

      if (httpReader != null) {
         if (httpReader.showUsage == true)
            HttpIorForCpp.usage();
         else
            System.out.println(httpReader.getIOR());
      }
   }

}
