/*
 * Copyright (c) 2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * $Id$
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.xmlBlaster.test.j2ee;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Enumeration;
import javax.naming.*;
import javax.naming.spi.InitialContextFactory;
/**
 * A partial impl of JNDI context to test J2ee services.
 *
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.1 $
 */

public class MemContext implements Context, InitialContextFactory{
   NameParser parser = new NamingParser ();
   static final Properties prop = new Properties();
   static {
         prop.setProperty("jndi.syntax.direction","left_to_right");
         prop.setProperty("jndi.syntax.separator","/");
   };
   
   class NamingParser
      implements NameParser {
      public Name parse(String name)
         throws NamingException {
         return new CompoundName(name,prop);
      }


   }
   String prefix;
   Hashtable env;
   Hashtable bindings;

   //--- Factory impl ---
   private static MemContext root;
   private static Object lock = new Object();
   /**
    * A simpel factory, that saves the first created root context in a static.
    */
   public Context getInitialContext(Hashtable env) throws NamingException{
      synchronized(lock) {
         if ( root == null) {
            root = new MemContext(env,"");
         } // end of if ()
      }
      return root;
   }

   public MemContext (){
      
   }
   public MemContext (Hashtable env, String name)throws NamingException {
      this.bindings = new Hashtable();
      this.env = new Hashtable();
      this.prefix = name;
      // Populating the environment hashtable
      if (env != null ) {
         Enumeration envEntries = env.keys();
         while (envEntries.hasMoreElements()) {
            String entryName = (String) envEntries.nextElement();
            addToEnvironment(entryName, env.get(entryName));
         }
      }
   }
   public MemContext (Hashtable env, String name, Hashtable bindings)throws NamingException{
      this(env,name);
      this.bindings = bindings;
   }


   private Context getNextContext(Object candidate, String name) throws NamingException{
      if ( candidate == null) {
         throw new NameNotFoundException(name);
      } // end of if ()
      if ( candidate instanceof Context) {
         return (Context)candidate;
      } else {
         throw new NamingException("Can not continue, " +name +" is not a subcontext");
      } // end of else
      
   }

   public Object addToEnvironment(String propName, Object propVal)
      throws NamingException {
      Object val = env.get(propName);
      env.put(propName,propVal);
      return val;
   }
   
   public Object lookup(Name name) throws NamingException {
      if ( name==null) {
         throw new NamingException("Name to allowed to be null");
      } // end of if ()
      if ( name.isEmpty()) {
         return new MemContext(env,prefix,bindings);
      } // end of if ()

      //Lookup on this comp
      String n = name.get(0);
      Object entry = bindings.get(n);

      if ( entry == null) {
         throw new NameNotFoundException(n);
      } // end of if ()
       
      // if name contains more components continue lookup
      if ( name.size()>1) {
         return getNextContext(entry,n).lookup(name.getSuffix(1));         
      } else {
         return entry;
      } // end of else
       
       
       
       
   }

   public Object lookup(String name) throws NamingException {
      return lookup (getNameParser (name).parse (name) ) ;
   }

   public void bind(Name name, Object obj) throws NamingException {
      if ( name==null) {
         throw new NamingException("Name to allowed to be null");
      } // end of if ()
       
      String n = name.get(0);
      Object entry = bindings.get(n);
       
      if ( name.size()>1 ) {
         getNextContext(entry,n).bind(name.getSuffix(1),obj);    
          
      } else {
         if ( entry != null) {
            throw new NameAlreadyBoundException(n);
         } // end of if ()
         bindings.put(n,obj);
      } // end of else
       
       
       
   }

   public void bind(String name, Object obj) throws NamingException {
      bind (getNameParser (name).parse (name), obj);
   }

   public void rebind(Name name, Object obj) throws NamingException {
      if ( name==null) {
         throw new NamingException("Name to allowed to be null");
      } // end of if ()
       
      String n = name.get(0);
      Object entry = bindings.get(n);
       
      if ( name.size()>1 ) {
         getNextContext(entry,n).rebind(name.getSuffix(1),obj);    
          
      } else {
         if ( entry != null) {
            unbind(n);
         } // end of if ()
         bind(n,obj);
      } // end of else
   }

   public void rebind(String name, Object obj) throws NamingException {
      rebind (getNameParser (name).parse (name), obj);
   }

   public void unbind(Name name) throws NamingException {
      if ( name==null) {
         throw new NamingException("Name to allowed to be null");
      } // end of if ()
      String n = name.get(0);
      Object entry = bindings.get(n);
      if ( name.size()>1 ) {
         getNextContext(entry,n).unbind(name.getSuffix(1));         
      } else {
         if ( entry == null) {
            throw new NameNotFoundException(n);
         } // end of if ()
         bindings.remove(n);
      } // end of else
       
   }
   
   public void unbind(String name) throws NamingException {
      unbind (getNameParser (name).parse (name));
   }
   
   public Context createSubcontext(Name name) throws NamingException {
      if ( name==null) {
         throw new NamingException("Name to allowed to be null");
      } // end of if ()
      
      String n = name.get(0);
      Object entry = bindings.get(n);
      if ( name.size()>1 ) {
         return getNextContext(entry,n).createSubcontext(name.getSuffix(1));         
      } else {
         if ( entry != null) {
            throw new NameAlreadyBoundException(n);
         } // end of if ()
         MemContext ctx = new MemContext(env,n);
         bindings.put(n,ctx);
         return ctx;
      } // end of else
   }
   public Context createSubcontext(String name) throws NamingException {
      return createSubcontext (getNameParser (name).parse (name));
   }

   public void close() throws NamingException {
      ;//NOOP
   }
   public String getNameInNamespace() throws NamingException {
      throw new OperationNotSupportedException();
   }

   public void rename(Name oldName, Name newName) throws NamingException {
      throw new OperationNotSupportedException();
   }

   public void rename(String oldName, String newName) throws NamingException {
      throw new OperationNotSupportedException();
   }

   public NamingEnumeration list(Name name) throws NamingException {
      throw new OperationNotSupportedException();
   }

   public NamingEnumeration list(String name) throws NamingException {
      throw new OperationNotSupportedException();
   }

   public NamingEnumeration listBindings(Name name) throws NamingException {
      throw new OperationNotSupportedException();
   }
   public NamingEnumeration listBindings(String name) throws NamingException {
      throw new OperationNotSupportedException();
   }
   public void destroySubcontext(Name name) throws NamingException {
      throw new OperationNotSupportedException();
   }
   public void destroySubcontext(String name) throws NamingException {
      throw new OperationNotSupportedException();
   }
   public Object lookupLink(Name name) throws NamingException {
      throw new OperationNotSupportedException();
   }
   public Object lookupLink(String name) throws NamingException {
      throw new OperationNotSupportedException();
   }
   public NameParser getNameParser(Name name) throws NamingException {
      return getNameParser (name.toString ());
   }
   public NameParser getNameParser(String name) throws NamingException {
      return parser;
   }
   public String composeName(String name, String prefix)
      throws NamingException {
      throw new OperationNotSupportedException();
   }

   public Name composeName(Name name, Name prefix)
      throws NamingException {
      throw new OperationNotSupportedException();
   }

   public Object removeFromEnvironment(String propName)
      throws NamingException {
      throw new OperationNotSupportedException();
   }
   public Hashtable getEnvironment() throws NamingException {
      throw new OperationNotSupportedException();
   }

   public static void main (String[] args) {
      try {
         System.setProperty(Context.INITIAL_CONTEXT_FACTORY ,"org.xmlBlaster.test.j2ee.MemContext");

         Context ctx = new InitialContext();
         Context hej = ctx.createSubcontext("hej");
         Context deep= ctx.createSubcontext("hej/deep");

         ctx.rebind("hej/deep/hello","Hello");
         String h = (String)ctx.lookup("hej/deep/hello");
         System.out.println(h);
         ctx.unbind("hej/deep/hello");
         try {
            h = (String)ctx.lookup("hej/deep/hello");
            throw new NamingException("Name found altough it was unbound"); 
         } catch (NamingException e) {
            
         } // end of try-catch

         // And the crucial test....
         ctx.rebind("hej/deep/hello","Hello");

         
         h = (String)new InitialContext().lookup("hej/deep/hello");
         System.out.println(h);
         
         
      } catch (Exception e) {
         e.printStackTrace();
      } // end of try-catch
      
   } // end of main ()
   

}// MemContext
