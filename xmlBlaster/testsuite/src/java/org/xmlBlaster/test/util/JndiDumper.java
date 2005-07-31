/*------------------------------------------------------------------------------
Name:      JndiDumper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.util;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.apache.naming.NamingService;


/**
 * JndiDumper is used to analize the specified context.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class JndiDumper {

   /**
    * Scans the context recursively
    * @param offset
    * @param context
    * @param out
    * @throws NamingException
    */
   public static void scanContext(String contextName, Context context, PrintStream out) throws NamingException {
      HashSet subCtx = new HashSet();
      if (context == null) context = new InitialContext();
      if (contextName == null) contextName = "/";
      out.println(contextName);
      StringBuffer buf = new StringBuffer(contextName.length());
      for (int i=0; i < contextName.length(); i++) buf.append(" ");
      String offset = buf.toString();
      NamingEnumeration enumer = context.list("");
      while (enumer.hasMore()) {
         NameClassPair pair = (NameClassPair)enumer.next();
         Object obj = context.lookup(pair.getName());
         if (obj instanceof Context) scanContext(contextName + pair.getName() + "/", (Context)obj, out);         
         else {
            out.println(offset +  pair.getName() + " class='" + pair.getClassName() + "'");            
//            out.println(tmpOffset + " class='" + pair.getClassName() + "'");            
         }
      }
   }

   /**
    * JNDI determines each property's value by merging the values from the following two sources, in order: 
    * <p/>
    * The first occurrence of the property from the constructor's environment parameter and (for appropriate properties) the applet parameters and system properties. 
    * The application resource files (jndi.properties).
    * <p/>
    * <pre>
    * com.sun.jndi.ldap.LdapCtxFactory
    * com.sun.jndi.fscontext.RefFSContextFactory
    * com.ibm.ejs.ns.jndi.CNInitialContextFactory
    * com.ibm.websphere.naming.WsnInitialContextFactory
    *
    * java -Djava.naming.factory.initial=com.ibm.websphere.naming.WsnInitialContextFactory org.xmlBlaster.test.util.JndiDumper -startNamingService true -fillNames true
    *
    * Dump rmiregistry:
    * java -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://localhost:1099 org.xmlBlaster.test.util.JndiDumper -fillNames false
    * </pre>
    */
   public static void main(String[] args) {
      try {
         boolean startNamingService = false;
         boolean fillNames = true;
         String factory = null;
         for (int i=0; i<args.length-1; i++) {
            if (args[i].equalsIgnoreCase("-startNamingService")) {
               startNamingService = (new Boolean(args[++i])).booleanValue();
            }
            else if (args[i].equalsIgnoreCase("-java.naming.factory.initial")) {
               factory = args[++i];
            }
            else if (args[i].equalsIgnoreCase("-fillNames")) {
               fillNames = (new Boolean(args[++i])).booleanValue();
            }
         }

         NamingService namingService = null;
         if (startNamingService) {
            namingService = new NamingService();
            namingService.start();
            System.out.println("Started an ebedded naming service.");
         }

         Hashtable properties = new Hashtable();
         if (factory != null) {
            properties.put("java.naming.factory.initial", factory);
            System.out.println("Forcing java.naming.factory.initial=" + factory);
         }
         else {
            System.out.println("Using System.getProperty(java.naming.factory.initial)=" + System.getProperty("java.naming.factory.initial"));
         }
         
         InitialContext context = new InitialContext(properties);
         if (fillNames) {
            context.bind("first", new String("first"));
            context.bind("second", new String("first"));
            context.bind("third", new String("first"));
            context.createSubcontext("dir1");
            context.createSubcontext("dir2");
            context.createSubcontext("dir3");
            context.bind("dir1/first", new String("first"));
            context.bind("dir1/second", new String("first"));
            context.bind("dir1/third", new String("first"));
                     
            Context ctx = (Context)context.lookup("dir2");
            ctx.bind("first", new String("first"));
            ctx.bind("second", new String("first"));
            ctx.bind("third", new String("first"));
         }
         
         System.out.println("================JNDI CONTENT START=============");
         JndiDumper.scanContext(null, context, System.out);
         System.out.println("================JNDI CONTENT END  =============");

         if (namingService != null) namingService.stop();
      }
      catch (Exception ex) {
         ex.getMessage();
         ex.printStackTrace();
      }
   }
}
