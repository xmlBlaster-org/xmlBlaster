/*------------------------------------------------------------------------------
Name:      JndiDumper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.test.util;

import java.io.PrintStream;
import java.util.HashSet;

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
      NamingEnumeration enum = context.list("");
      while (enum.hasMore()) {
         NameClassPair pair = (NameClassPair)enum.next();
         Object obj = context.lookup(pair.getName());
         if (obj instanceof Context) scanContext(contextName + pair.getName() + "/", (Context)obj, out);         
         else {
            out.println(offset +  pair.getName() + " class='" + pair.getClassName() + "'");            
//            out.println(tmpOffset + " class='" + pair.getClassName() + "'");            
         }
      }
   }
   
   public static void main(String[] args) {
      try {
         NamingService namingService = new NamingService();
         namingService.start();
         
         InitialContext context = new InitialContext();
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
         
         JndiDumper.scanContext(null, null, System.out);

         namingService.stop();
      }
      catch (Exception ex) {
         ex.getMessage();
         ex.printStackTrace();
      }
   }
}
