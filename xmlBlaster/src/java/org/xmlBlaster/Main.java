/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Main class to invoke the xmlBlaster server
           $Revision: 1.6 $ $Date: 1999/11/15 09:35:48 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.engine.*;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.authenticateIdl.*;
import java.io.*;
import org.omg.CosNaming.*;


/**
 * Main class to invoke the xmlBlaster server
 */
public class Main
{
   final private String ME = "Main";
   static public org.omg.CORBA.ORB orb;

   public Main( String[] args )
   {
      orb = org.omg.CORBA.ORB.init(args, null);
      try {
         org.omg.PortableServer.POA poa = 
         org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

         // USING TIE:
         org.omg.PortableServer.Servant servantAuth = new AuthServerPOATie(new AuthServerImpl(orb));
         // NOT TIE:
         // org.omg.PortableServer.Servant servantAuth = new AuthServerImpl(orb);

         org.omg.CORBA.Object o = poa.servant_to_reference(servantAuth);

         if( args.length == 1 ) {
            // write the object reference to args[0]

            PrintWriter ps = new PrintWriter(new FileOutputStream(new File( args[0] )));
            ps.println( orb.object_to_string( o ) );
            ps.close();
         } 
         else {
            NamingContext nc = NamingContextHelper.narrow(orb.resolve_initial_references("NameService"));
            NameComponent [] name = new NameComponent[1];
            name[0] = new NameComponent();
            name[0].id = "xmlBlaster-Authenticate";
            name[0].kind = "MOM";

            nc.bind(name, o);
         }
      } catch ( Exception e ) {
         e.printStackTrace();
         Log.panic(ME, e.toString());
      }

      orb.run();
   }


   /**
    *  Invoke: jaco org.xmlBlaster.Main
    */
   public static void main( String[] args )
   {
      new Main(args);
   }
}
