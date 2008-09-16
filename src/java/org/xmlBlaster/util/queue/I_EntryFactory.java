/*------------------------------------------------------------------------------
Name:      I_EntryFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for parsing from I_QueueEntry to byte[] and back
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import java.io.InputStream;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.queue.jdbc.XBMeat;
import org.xmlBlaster.util.queue.jdbc.XBRef;
import org.xmlBlaster.util.queue.jdbc.XBStore;

/**
 * The Interface which can be used to convert an object which implements the
 * interface I_Entry to a byte[] object and back. This is useful for
 * example if you want to store such entries in persistent storage like a
 * database or a file system. It might however be used even for other
 * purposes.
 * @author michele@laghi.eu
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
    *
    * @param priority The priority of the entry (the queues first sort criteria)
    * @param timestamp The queues second sort criteria
    * @param type The type of the entry, used to know how to parse it
    * @param persistent true: the entry is persistent, false: the entry is swapped from cache (no more RAM memory)
    * @param sizeInBytes The approximate, immutable size that the entry occupies in RAM,
    *        this can be totally different
    *        to the size the entry occupies on storage
    * @param obj The serialized data (formatted as given by 'type')
    * @param storageId A unique identifier of the queue
    */
   /*
   I_Entry createEntry(int priority, long timestamp, String type, boolean persistent,
                       long sizeInBytes, byte[] obj, StorageId storageId)
      throws XmlBlasterException;
*/
   I_Entry createEntry(int priority, long timestamp, String type, boolean persistent,
                       long sizeInBytes, InputStream is, StorageId storageId)
      throws XmlBlasterException;

   /**
    * Is called after the instance is created.
    */
   public void initialize(Global glob);

   /**
    * Allows to overwrite properties which where passed on initialize()
    * The properties which support hot configuration are depending on the used implementation
    */
   public void setProperties(Object userData);

   /**
    * Access the current Parser configuration
    */
   public Object getProperties();

   /**
    * Added for 2008 Queues
    * @param meat The meat of the message (could be null if a reference)
    * @param ref The reference of the message (should never be null)
    * @param storageId the storageId
    * @return the newly created I_Entry
    * @throws XmlBlasterException
    */
   I_Entry createEntry(XBStore store, XBMeat meat, XBRef ref) throws XmlBlasterException;

   
}
