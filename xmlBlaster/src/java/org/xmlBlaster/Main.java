package org.xmlBlaster;

import java.io.*;
import org.omg.CosNaming.*;

public class Main
{
    public static void main( String[] args )
    {
        org.omg.CORBA.ORB orb = org.omg.CORBA.ORB.init(args, null);
        try
        {
            org.omg.PortableServer.POA poa = 
            org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));

            org.omg.CORBA.Object o = poa.servant_to_reference(new ServerImpl());

            if( args.length == 1 ) 
            {
                // write the object reference to args[0]

                PrintWriter ps = new PrintWriter(new FileOutputStream(new File( args[0] )));
                ps.println( orb.object_to_string( o ) );
                ps.close();
            } 
            else
            {
                // CORBA compliant:

                NamingContext nc = NamingContextHelper.narrow(orb.resolve_initial_references("NameService"));
                NameComponent [] name = new NameComponent[1];
                name[0] = new NameComponent();
                name[0].id = "xmlBlaster";
                name[0].kind = "MOM";

                nc.bind(name, o);

                // alternatively, the proprietary JacORB API can be used 
                // as a more conveniant way of doing the same thing.
                // In this case, the kind field in the name is set to
                // "service" implicitly

                // jacorb.naming.NameServer.registerService( o, "grid" );
            }
        } 
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        //System.exit(0);
        orb.run();
    }
}
