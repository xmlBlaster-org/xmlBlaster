/*------------------------------------------------------------------------------
Name:      PropertiesInfo.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

/**
 * 
 * PropertiesInfo This is the I_Info implementation making use of Properties.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class PropertiesInfo implements I_Info {
        
   Properties props;
   Map objects;
   
   private InfoHelper helper;
   
   
   /**
    * Creates a simple implementation based on java's Properties.
    * This implementation uses the reference to the properties passed. If you want a snapshot of these properties, you 
    * need to take a clone and pass the clone to the constructor.
    * 
    * @param props The configuration store
    */
   public PropertiesInfo(Properties props) {
      this.props = props;
      this.objects = new HashMap();
      this.helper = new InfoHelper(this);
      this.helper.replaceAllEntries(this, null);
   }
   
   public String getRaw(String key) {
      return this.props.getProperty(key);
   }
   
   /**
   * @see org.xmlBlaster.contrib.I_Info#get(java.lang.String, java.lang.String)
   */
   public String get(String key, String def) {
      if (def != null)
         def = this.helper.replace(def);
      if (key == null)
         return def;
      key = this.helper.replace(key);
      String ret = getRaw(key);
      if (ret != null) {
         return this.helper.replace(ret);
      }
      return def;
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#put(java.lang.String, java.lang.String)
    */
    public void putRaw(String key, String value) {
       if (value == null)
         this.props.remove(key);
       else
          this.props.put(key, value);
    }

    /**
     * @see org.xmlBlaster.contrib.I_Info#put(java.lang.String, java.lang.String)
     */
     public void put(String key, String value) {
        if (key != null)
           key = this.helper.replace(key);
        if (value != null)
           value = this.helper.replace(value);
        if (value == null)
          this.props.remove(key);
        else
           this.props.put(key, value);
     }

   /**
   * @see org.xmlBlaster.contrib.I_Info#getLong(java.lang.String, long)
   */
   public long getLong(String key, long def) {
      if (key == null)
         return def;
      String ret = get(key, null);
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
   public int getInt(String key, int def) {
      if (key == null)
         return def;
      String ret = get(key, null);
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
    public boolean getBoolean(String key, boolean def) {
       if (key == null)
          return def;
       String ret = get(key, null);
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
   public Object getObject(String key) {
      return this.objects.get(key);
   }

   /**
   * @see org.xmlBlaster.contrib.I_Info#putObject(java.lang.String, Object)
   */
   public Object putObject(String key, Object o) {
      return this.objects.put(key, o);
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_Info#getKeys()
    */
   public Set getKeys() {
      return this.props.keySet();
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getObjectKeys()
    */
   public Set getObjectKeys() {
      return this.objects.keySet();
   }
   
   public static void addSet(Set dest, Set source) {
      if (dest == null || source == null)
         return;
      Iterator iter = source.iterator();
      while (iter.hasNext()) {
         String key = (String)iter.next();
         dest.add(key);
      }
      
   }
   
}
