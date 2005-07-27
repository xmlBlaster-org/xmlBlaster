/*------------------------------------------------------------------------------
Name:      I_AdminQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares available methods of a queue implementation for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by I_Queue.java implementations delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.4
 * @see org.xmlBlaster.util.queue.I_Queue
 */
public interface I_AdminQueue {
   //public void setProperties(java.lang.Object)       throws org.xmlBlaster.util.XmlBlasterException;
   public String getPropertyStr();
   public boolean isNotifiedAboutAddOrRemove();
   public long[] getEntryReferences()       throws org.xmlBlaster.util.XmlBlasterException;
   public java.util.ArrayList getEntries()       throws org.xmlBlaster.util.XmlBlasterException;
   public String getStorageIdStr();
   public java.util.ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)       throws org.xmlBlaster.util.XmlBlasterException;
   //public java.util.ArrayList takeLowest(int, long, org.xmlBlaster.util.queue.I_QueueEntry, boolean)       throws org.xmlBlaster.util.XmlBlasterException;
   //public java.util.ArrayList peekLowest(int, long, org.xmlBlaster.util.queue.I_QueueEntry, boolean)       throws org.xmlBlaster.util.XmlBlasterException;
   public String peekStr()       throws Exception;
   public String[] peekEntries(int numOfEntries)       throws Exception;
   //public java.util.ArrayList peek(int, long)       throws org.xmlBlaster.util.XmlBlasterException;
   //public java.util.ArrayList peekSamePriority(int, long)       throws org.xmlBlaster.util.XmlBlasterException;
   //public java.util.ArrayList peekWithPriority(int, long, int, int)       throws org.xmlBlaster.util.XmlBlasterException;
   //public java.util.ArrayList peekWithLimitEntry(org.xmlBlaster.util.queue.I_QueueEntry)       throws org.xmlBlaster.util.XmlBlasterException;
   //public long removeWithLimitEntry(org.xmlBlaster.util.queue.I_QueueEntry, boolean)       throws org.xmlBlaster.util.XmlBlasterException;
   public int remove()       throws org.xmlBlaster.util.XmlBlasterException;
   public long remove(long numOfEntries, long numOfBytes) throws org.xmlBlaster.util.XmlBlasterException;
   public long removeWithPriority(long numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws org.xmlBlaster.util.XmlBlasterException;
   public long getNumOfEntries();
   public long getNumOfPersistentEntries();
   public long getMaxNumOfEntries();
   public long getNumOfBytes();
   public long getNumOfPersistentBytes();
   public long getMaxNumOfBytes();
   //public boolean[] removeRandom(org.xmlBlaster.util.queue.I_Entry[])       throws org.xmlBlaster.util.XmlBlasterException;
   //public int removeRandom(org.xmlBlaster.util.queue.I_Entry)       throws org.xmlBlaster.util.XmlBlasterException;
   public int removeTransient()       throws org.xmlBlaster.util.XmlBlasterException;
   public boolean isTransient();
   public long clear();
   public void shutdown();
   //public long removeHead(org.xmlBlaster.util.queue.I_QueueEntry)       throws org.xmlBlaster.util.XmlBlasterException;
   public boolean isShutdown();
   //public void addQueueSizeListener(org.xmlBlaster.util.queue.I_QueueSizeListener);
   //public void removeQueueSizeListener(org.xmlBlaster.util.queue.I_QueueSizeListener);
   //public boolean hasQueueSizeListener(org.xmlBlaster.util.queue.I_QueueSizeListener);
   public java.lang.String usage();
   public java.lang.String toXml();
}
