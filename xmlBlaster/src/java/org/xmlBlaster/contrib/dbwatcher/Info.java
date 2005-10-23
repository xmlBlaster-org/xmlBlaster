/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.xmlBlaster.contrib.I_Info;

/**
 * Simple container for environment settings. 
 * <p>
 * This is a dummy implementation for the DbWatcher examples and testsuite.  
 * </p> 
 * @author Marcel Ruff
 */
public class Info implements I_Info {
        
   Preferences prefs;
   Map objects;
   
   /**
    * Creates a simple implementation based on java's Preferences
    * @param prefs The configuration store
    */
   public Info(Preferences prefs) {
      this.prefs = prefs;
      this.objects = new HashMap();
   }
   
   /**
   * @see org.xmlBlaster.contrib.I_Info#get(java.lang.String, java.lang.String)
   */
   public String get(String key, String def) {
      return this.prefs.get(key, def);
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#put(java.lang.String, java.lang.String)
    */
    public void put(String key, String value) {
       if (value == null)
         this.prefs.remove(key);
       else
          this.prefs.put(key, value);
    }

   /**
   * @see org.xmlBlaster.contrib.I_Info#getLong(java.lang.String, long)
   */
   public long getLong(String key, long def) {
      return this.prefs.getLong(key, def);
   }

   /**
   * @see org.xmlBlaster.contrib.I_Info#getInt(java.lang.String, int)
   */
   public int getInt(String key, int def) {
      return this.prefs.getInt(key, def);
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getBoolean(java.lang.String, boolean)
    */
    public boolean getBoolean(String key, boolean def) {
       return this.prefs.getBoolean(key, def);
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
      try {
         String[] tmp = this.prefs.childrenNames();
         Set set = new TreeSet();
         for (int i=0; i < tmp.length; i++)
            set.add(tmp[i]);
         return set;
      }
      catch (BackingStoreException ex) {
         ex.printStackTrace();
         return new TreeSet();
      }
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getObjectKeys()
    */
   public Set getObjectKeys() {
      return this.objects.keySet();
   }
   
   
   
}
