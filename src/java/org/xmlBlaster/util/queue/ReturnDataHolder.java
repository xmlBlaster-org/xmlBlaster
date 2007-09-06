/*------------------------------------------------------------------------------
Name:      ReturnDataHolder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;
import java.util.ArrayList;


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
   public final ArrayList list = new ArrayList();

}
