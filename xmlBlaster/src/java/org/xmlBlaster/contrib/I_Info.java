/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib;

import java.util.Set;

/**
 * Hides configuration parameters and passes common objects. 
 * <p>
 * To embed the DbWatcher into your project you need to write
 * a plugin which implements this interface and forwards all calls
 * to you configuration framework.
 * </p>
 * @author Marcel Ruff
 */
public interface I_Info {
   
   /**
    * Access a string environment setting. 
    * @param key The parameter key
    * @param def The default used if key is not found
    * @return The configured value of the parameter
    */
   String get(String key, String def);
   
   /**
    * Put key/value to environment.  
    * @param key The parameter key
    * @param value The parameter value, if null the parameter is removed.
    * @throws NullPointerException if <tt>key</tt> is <tt>null</tt>.
    */
   void put(String key, String value);
        
   /**
    * Access an environment setting of type long. 
    * @param key The parameter key
    * @param def The default used if key is not found
    * @return The configured value of the parameter
    */
   long getLong(String key, long def);
        
   /**
    * Access an environment setting of type int. 
    * @param key The parameter key
    * @param def The default used if key is not found
    * @return The configured value of the parameter
    */
   int getInt(String key, int def);

   /**
    * Access an environment setting of type boolean. 
    * @param key The parameter key
    * @param def The default used if key is not found
    * @return The configured value of the parameter
    */
   boolean getBoolean(String key, boolean def);

   /**
    * Store an object.  
    * @param key The object key
    * @param o The object to remember or null to remove it
    * @return The old object or null
    */
   Object putObject(String key, Object o);

   /**
    * Access the remembered object.  
    * @param key The object key
    * @return The found object or null
    */
   Object getObject(String key);
   
   /**
    * Gets the keys of the entries stored. Note that this does not return the
    * key of the entries stored as objects. To retrieve these use getObjectKeys().
    * @return
    */
   Set getKeys();
   
   /**
    * Gets the keys of the objects registered. Note that this does not return the
    * key of the normal entries. To retrieve these use getKeys().
    * @return
    */
   Set getObjectKeys();
   
}
