/*------------------------------------------------------------------------------
Name:      ReturnDataHolder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;
import java.util.ArrayList;
import java.util.List;

import org.xmlBlaster.util.queue.jdbc.XBRef;


/**
 * DataHolder Object to hold the return data of methods used in the different
 * implementations of the I_Queue and their helper classes.
 * 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 *
 */
public class ReturnDataHolder {
   public long countEntries = 0;
   public long countBytes = 0L;
   public long countPersistentEntries = 0;
   public long countPersistentBytes = 0L;
   public ArrayList<I_Entry> list = new ArrayList<I_Entry>();
   public List<XBRef> refList;// = new ArrayList<XBRef>();
}
