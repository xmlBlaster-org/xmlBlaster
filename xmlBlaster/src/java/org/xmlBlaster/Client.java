package org.xmlBlaster;

import jacorb.naming.NameServer;
import org.omg.CosNaming.*;

public class Client
{
   public static void main(String args[]) 
   { 
      try {
         Server xmlServer;
         org.omg.CORBA.ORB orb = org.omg.CORBA.ORB.init(args,null);

         if(args.length==1 ) {
             // args[0] is an IOR-string 

             xmlServer = ServerHelper.narrow(orb.string_to_object(args[0]));
         } 
         else {
            // CORBA compliant:
            NamingContext nc = NamingContextHelper.narrow(orb.resolve_initial_references("NameService"));
            NameComponent [] name = new NameComponent[1];
            name[0] = new NameComponent();
            name[0].id = "xmlBlaster";
            name[0].kind = "MOM";

            xmlServer = ServerHelper.narrow(nc.resolve(name));
         }

         String xmlKey = "HALLO";
         String qos = "STRONG";
         xmlServer.subscribe(xmlKey, qos);

         xmlServer._release();
         System.out.println("done. ");

      }
      catch (Exception e) {
          e.printStackTrace();
      }
   }
}
