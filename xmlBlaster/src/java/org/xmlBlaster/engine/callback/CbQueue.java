/*------------------------------------------------------------------------------
Name:      CbQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: CbQueue.java,v 1.1 2000/12/29 14:46:22 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import java.util.*;


/**
 * Queueing messages to send back to a client.
 */
public class CbQueue
{
   /**
    * The minimum priority of a message.
    */
   public final static int MIN_PRIORITY = 10;

   /**
    * The default priority of a message.
    */
   public final static int NORM_PRIORITY = 5;

   /**
   * The maximum priority of a message.
   */
   public final static int MAX_PRIORITY = 1;

   private SortedSet sortedSet = Collections.synchronizedSortedSet(new TreeSet());

   /**
    * Sorts the messages
    * <ol>
    *   <li>Priority</li>
    *   <li>Timestamp</li>
    * </ol>
    */
   class MyComparator implements Comparator
   {
      public int compare(Object o1, Object o2)
      {
         if (Log.TRACE) Log.trace("", "Comparing ...");
         return getKey((MessageUnitWrapper)o1).compareToIgnoreCase(getKey((MessageUnitWrapper)o2));
      }
      public boolean equals(Object obj)
      {
         return this.equals(obj);
      }
      final String getKey(MessageUnitWrapper pp)
      {
         return ""; /* + pp.getTimestamp() + "." + pp.getPriority() */
      }
   }
}

