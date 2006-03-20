/*------------------------------------------------------------------------------
Name:      InfoHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.util.I_ReplaceVariable;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.qos.ClientProperty;


/**
 * InfoHelper offers helper methods for operations on I_Info objects.
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class InfoHelper {

   private static Logger log = Logger.getLogger(InfoHelper.class.getName());

   class Replacer implements I_ReplaceVariable {
      
      I_Info info;
      
      public Replacer(I_Info info) {
         this.info = info;
      }
      
      public String get(String key) {
         if (key == null)
            return null;
         String tmp = this.info.getRaw(key);
         if (tmp == null)
            return null;
         return tmp.trim();
      }
   }
   
   private ReplaceVariable replaceVariable;
   private Replacer replacer;
   
   
   public InfoHelper(I_Info info) {
      this.replaceVariable = new ReplaceVariable();
      this.replacer = new Replacer(info);
   }

   public final String replace(String txt) {
      return this.replaceVariable.replace(txt, this.replacer);
   }


   
   public void replaceAllEntries(I_Info info, Set keysToIgnore) {
      synchronized (info) {
         Set set = info.getKeys();
         String[] keys = (String[])set.toArray(new String[set.size()]);
         for (int i=0; i < keys.length; i++) {
            String key = keys[i];
            if (key == null) {
               log.warning("entry found which had a null key");
               continue;
            }
            if (keysToIgnore != null && keysToIgnore.contains(key))
               continue;
            String newKey = replace(key);
            if (newKey != null && !newKey.equals(key)) { // then the key had a token
               String val = replace(info.getRaw(key)); // get the cleaned old value
               // check if the cleaned key is already in use
               String oldValueOfNewKey = info.get(newKey, null);
               if (oldValueOfNewKey == null || oldValueOfNewKey.equals(val)) {
                  info.putRaw(key, null); // erase the old entry
                  info.put(newKey, val);
               } // otherwise keep the old one.
            }
         }
      }
   }
   
   
   /**
    * Returns the subset of properties found in the I_Info object starting 
    * with the specified prefix. 
    * @param prefix The prefix to use. If null is passed, then all properties
    * are returned
    * @param info The I_Info object on which to operate
    * @param dbHelper the DbMetaHelper used to determine if the key and value have
    * to be moved to uppercase/lowcase or left untouched. Can be null, in which case it
    * is ignored.
    * 
    * @return the subset of properties found. The keys are stripped from their
    * prefix. The returned keys are returned in alphabetical order.
    */
   public static Map getPropertiesStartingWith(String prefix, I_Info info, DbMetaHelper dbHelper) {
      synchronized (info) {
         Iterator iter = info.getKeys().iterator();
         TreeMap map = new TreeMap();
         while (iter.hasNext()) {
            String key = ((String)iter.next()).trim();
            if (prefix == null || key.startsWith(prefix)) {
               String val = info.get(key, null);
               if (prefix != null)
                  key = key.substring(prefix.length());
               if (dbHelper != null) {
                  key = dbHelper.getIdentifier(key);
                  val = dbHelper.getIdentifier(val);
               }
               log.info("found and adding key='" + key + "' value='" + val + "' on map for prefix='" + prefix + "'");
               map.put(key, val);
            }
         }
         return map;
      }
   }

   /**
    * Fills the I_Info with the entries of the map. If the values of the map are strings they are added
    * if they are ClientProperties they are added too, otherwise they are added as objects.
    */
   public static void fillInfoWithEntriesFromMap(I_Info info, Map map) {
      synchronized (info) {
         synchronized (map) {
            Iterator iter = map.keySet().iterator();
            while (iter.hasNext()) {
               String key = ((String)iter.next()).trim();
               Object obj = map.get(key);
               if (obj instanceof String) {
                  info.put(key, (String)obj);
               }
               else if (obj instanceof ClientProperty) {
                  info.put(key, ((ClientProperty)obj).getStringValue());
               }
               else {
                  info.putObject(key, obj);
               }
            }
         }
      }
   }

   /**
    * Returns a string containing all entries found. They are separated by what's specified as the separator,
    * @param iter
    * @return
    */
   public static String getIteratorAsString(Iterator iter, final String separator) {
      StringBuffer buf = new StringBuffer();
      boolean isFirst = true;
      while (iter.hasNext()) {
         if (isFirst)
            isFirst = false;
         else
            buf.append(separator);
         buf.append(iter.next());
      }
      return buf.toString();
   }
   
   public static String getIteratorAsString(Iterator iter) {
      return getIteratorAsString(iter, ",");
   }
   
   /**
    * Prints the stack trace as a String so it can be put on the normal logs.
    * @param ex The exception for which to write out the stack trace. If you pass null it will print the Stack trace of
    * a newly created exception.
    * @return The Stack trace as a String.
    */
   public static String getStackTraceAsString(Throwable ex) {
      // this is just to send the stack trace to the log file (stderr does not go there)
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream pstr = new PrintStream(baos);
      if (ex == null)
         ex = new Exception();
      ex.printStackTrace(pstr);
      return new String(baos.toByteArray());
   }

}
