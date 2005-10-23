/*------------------------------------------------------------------------------
Name:      ClientPropertiesInfo.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import org.xmlBlaster.util.I_ReplaceVariable;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * ClientPropertiesInfo This is the I_Info implementation making use of Properties.
 * Creates a simple implementation based on our ClientProperty maps.
 * This implementation uses the reference to the properties passed. If you want a snapshot of these properties, you 
 * need to take a clone and pass the clone to the constructor.
 * Therefore this class can be seen as a decorator to the map passed
 * into the constructor. If you change a value with this class it will
 * update the clientPropertyMap. If entries in the map are found which
 * are not of the type ClientProperty, they are ignored.
 * 
 * This class is thread safe.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ClientPropertiesInfo implements I_Info {
        
   Map clientPropertyMap;
   Map objects;
   
   class Replacer implements I_ReplaceVariable {
      public String get(String key) {
         if (key == null)
            return null;
         ClientProperty prop = (ClientProperty)clientPropertyMap.get(key);
         if (prop != null) {
            String repl = prop.getStringValue();
            if (repl != null)
               return repl.trim();
         }
         return null;
      }
   }
   
   private Replacer replacer;
   private ReplaceVariable replaceVariable;

   
   public ClientPropertiesInfo(Map clientPropertyMap) {
      this(clientPropertyMap, null);
   }   
   
   /**
    * 
    * @param props The configuration store
    */
   public ClientPropertiesInfo(Map clientPropertyMap, I_Info extraInfo) {
      this.clientPropertyMap = clientPropertyMap;
      if (this.clientPropertyMap == null)
         this.clientPropertyMap = new HashMap();
      this.objects = new HashMap();
      this.replacer = new Replacer();
      this.replaceVariable = new ReplaceVariable();
      
      if (extraInfo != null) {
         synchronized (extraInfo) {
            Iterator iter = extraInfo.getKeys().iterator();
            while (iter.hasNext()) {
               String key = (String)iter.next();
               String obj = extraInfo.get(key, null);
               if (obj != null)
                  put(key, obj);
            }
         }
      }
   }

/*   
   HashMap pubMap = new HashMap();
   synchronized (this.info) {
      Iterator iter = this.info.getKeys().iterator();
      while (iter.hasNext()) {
         String key = (String)iter.next();
         String obj = this.info.get(key, null);
         if (obj != null)
            pubMap.put(key, obj);
      }
   }
*/   
   
   
   
   
   /**
    * 
    * @param txt
    * @return
    */
   protected final String replace(String txt) {
      return this.replaceVariable.replace(txt, this.replacer);
   }

   protected String getPropAsString(String key) {
      Object obj = this.clientPropertyMap.get(key);
      if (obj == null)
         return null;
      if (!(obj instanceof ClientProperty))
         return null;
      
      ClientProperty prop = (ClientProperty)obj;
      String ret = prop.getStringValue();
      if (ret != null) {
         return replace(ret);
      }
      return null;
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_Info#get(java.lang.String, java.lang.String)
    */
   public synchronized String get(String key, String def) {
      if (def != null)
         def = replace(def);
      if (key == null)
         return def;
      key = replace(key);
      String ret = getPropAsString(key);
      if (ret != null) {
         return replace(ret);
      }
      return null;
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#put(java.lang.String, java.lang.String)
    */
    public synchronized void put(String key, String value) {
       if (value == null)
         this.clientPropertyMap.remove(key);
       else {
          ClientProperty prop = new ClientProperty(key, null, null, value);
          this.clientPropertyMap.put(key, prop);
       }
    }

   /**
   * @see org.xmlBlaster.contrib.I_Info#getLong(java.lang.String, long)
   */
   public synchronized long getLong(String key, long def) {
      if (key == null)
         return def;
      String ret = this.getPropAsString(key);
      if (ret != null) {
         try {
            return Long.parseLong(ret);
         }
         catch (NumberFormatException ex) {
            ex.printStackTrace();
            return def;
         }
      }
      return def;
   }

   /**
   * @see org.xmlBlaster.contrib.I_Info#getInt(java.lang.String, int)
   */
   public synchronized int getInt(String key, int def) {
      if (key == null)
         return def;
      String ret = this.getPropAsString(key);
      if (ret != null) {
         try {
            return Integer.parseInt(ret);
         }
         catch (NumberFormatException ex) {
            ex.printStackTrace();
            return def;
         }
      }
      return def;
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getBoolean(java.lang.String, boolean)
    */
    public synchronized boolean getBoolean(String key, boolean def) {
       if (key == null)
          return def;
       String ret = this.getPropAsString(key);
       if (ret != null) {
          try {
             Boolean bool = new Boolean(ret);
             return bool.booleanValue();
          }
          catch (NumberFormatException ex) {
             ex.printStackTrace();
             return def;
          }
       }
       return def;
    }

   /**
   * @see org.xmlBlaster.contrib.I_Info#getObject(java.lang.String)
   */
   public synchronized Object getObject(String key) {
      return this.objects.get(key);
   }

   /**
   * @see org.xmlBlaster.contrib.I_Info#putObject(java.lang.String, Object)
   */
   public synchronized Object putObject(String key, Object o) {
      return this.objects.put(key, o);
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getKeys()
    */
   public synchronized Set getKeys() {
      Set set = new HashSet();
      Iterator iter = this.clientPropertyMap.entrySet().iterator();
      while (iter.hasNext()) {
         Map.Entry entry = (Map.Entry)iter.next();
         if (entry.getValue() instanceof ClientProperty)
            set.add(entry.getKey());
      }
      return set;
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getObjectKeys()
    */
   public synchronized Set getObjectKeys() {
      return this.objects.keySet();
   }
   
}
