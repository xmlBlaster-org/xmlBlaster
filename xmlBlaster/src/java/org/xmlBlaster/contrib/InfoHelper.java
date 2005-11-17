/*------------------------------------------------------------------------------
Name:      InfoHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.xmlBlaster.contrib.db.DbMetaHelper;


/**
 * InfoHelper offers helper methods for operations on I_Info objects.
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class InfoHelper {

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
               map.put(key, val);
            }
         }
         return map;
      }
   }
}
