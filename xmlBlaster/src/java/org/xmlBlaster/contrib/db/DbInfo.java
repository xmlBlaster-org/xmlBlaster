/*------------------------------------------------------------------------------
Name:      DbInfo.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.db;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * DbInfo This is the I_Info implementation making use of Properties.
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
public class DbInfo implements I_Info {

   private static Logger log = Logger.getLogger(DbInfo.class.getName());
   private final static String TABLE_NAME = "DBINFO";
   Map objects;
   private InfoHelper helper;
   private DbStorage storage;
   
   /**
    * @param clientPropertyMap Can be null
    */
   public DbInfo(I_DbPool pool, String id) throws Exception {
      this.storage = new DbStorage(pool, TABLE_NAME, id);
      this.helper = new InfoHelper(this);
      this.objects = new HashMap();
      // performance impact. It should anyway be clean what is in the Db.
      // this.helper.replaceAllEntries(this, null);
   }   
   
   /**
    * 
    * @param txt
    * @return
    */
   public String getRaw(String key) {
      try {
         ClientProperty prop = this.storage.getProperty(key);
         if (prop == null)
            return null;
         return prop.getStringValue();
      }
      catch (Exception ex) {
         log.warning("An exception occured when retrieving the entry '" + key + "': " + ex.getMessage());
         return null;
      }
   }
   
   
   /**
    * 
    * @param txt
    * @return
    */
   protected String getPropAsString(String key) {
      try {
         ClientProperty prop = this.storage.getProperty(key);
         if (prop == null)
            return null;
         String ret = prop.getStringValue();
         if (ret != null) {
            return this.helper.replace(ret);
         }
         return null;
      }
      catch (Exception ex) {
         log.warning("An exception occured when retrieving the entry '" + key + "': " + ex.getMessage());
         return null;
      }
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_Info#get(java.lang.String, java.lang.String)
    */
   public synchronized String get(String key, String def) {
      if (def != null)
         def = this.helper.replace(def);
      if (key == null)
         return def;
      key = this.helper.replace(key);
      String ret = getPropAsString(key);
      if (ret != null) {
         return this.helper.replace(ret);
      }
      return null;
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#put(java.lang.String, java.lang.String)
    */
    public synchronized void put(String key, String value) {
       if (key != null)
          key = this.helper.replace(key);
       if (value != null)
          value = this.helper.replace(value);
       try {
          if (value == null)
             this.storage.remove(key);
           else {
              ClientProperty prop = new ClientProperty(key, null, null, value);
              this.storage.put(prop);
           }
       }
       catch (Exception ex) {
          log.warning("An exception occured when putting the entry '" + key + "': " + ex.getMessage());
       }
    }

    /**
     * @see org.xmlBlaster.contrib.I_Info#put(java.lang.String, java.lang.String)
     */
     public synchronized void putRaw(String key, String value) {
        try {
           if (value == null)
              this.storage.remove(key);
            else {
               ClientProperty prop = new ClientProperty(key, null, null, value);
               this.storage.put(prop);
            }
        }
        catch (Exception ex) {
           log.warning("An exception occured when putting the raw the entry '" + key + "': " + ex.getMessage());
        }
     }

   /**
   * @see org.xmlBlaster.contrib.I_Info#getLong(java.lang.String, long)
   */
   public synchronized long getLong(String key, long def) {
      if (key == null)
         return def;
      String ret = getPropAsString(key);
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
      String ret = getPropAsString(key);
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
       String ret = getPropAsString(key);
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
      try {
         return this.storage.getKeys();
      }
      catch (Exception ex) {
         log.warning("An exception occured when retreiving the keys: " + ex.getMessage());
         return new TreeSet();
      }
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getObjectKeys()
    */
   public synchronized Set getObjectKeys() {
      return this.objects.keySet();
   }

}
