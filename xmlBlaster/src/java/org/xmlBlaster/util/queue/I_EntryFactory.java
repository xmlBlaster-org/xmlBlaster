/*------------------------------------------------------------------------------
Name:      I_EntryFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for parsing from I_QueueEntry to byte[] and back
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queue.I_Entry;

/**
 * The Interface which can be used to convert an object which implements the
 * interface I_Entry to a byte[] object and back. This is useful for
 * example if you want to store such entries in persitent storage like a
 * database or a file system. It might however be used even for other
 * purposes.
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public interface I_EntryFactory
{
   /**
    * Parses the specified entry to a byte array (serializing).
    */
   byte[] toBlob(I_Entry entry) throws XmlBlasterException;

   /**
    * Parses back the raw data to a I_Entry (deserializing)
    */
   I_Entry createEntry(int priority, long timestamp, String type, boolean persistent, byte[] obj, StorageId storageId)
      throws XmlBlasterException;

   /**
    * Returns the name of this plugin
    */
   String getName();

   /**
    * Is called after the instance is created.
    * @param name A name identifying this plugin.
    */
   public void initialize(Global glob, String name);

   /**
    * Allows to overwrite properties which where passed on initialize()
    * The properties which support hot configuration are depending on the used implementation
    */
   public void setProperties(Object userData);

   /**
    * Access the current Parser configuration
    */
   public Object getProperties();

}
