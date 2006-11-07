/*------------------------------------------------------------------------------
Name:      DbInfoMBean.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.db;

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
public interface DbInfoMBean {
   String get(String key, String def);
   void put(String key, String value);
   String getKeysAsString();
   String getObjectKeysAsString();
}
