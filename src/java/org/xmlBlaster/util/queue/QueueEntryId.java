/*------------------------------------------------------------------------------
Name:      QueueEntryId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Class encapsulating the unique id for the message entries in queue
Version:   $Id$
Author:    xmlBlaster@marcelruff.info, laghi@swissinfo.org
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;
import org.xmlBlaster.util.Timestamp;

public class QueueEntryId extends Timestamp 
{
   /**
    * This class just hides the fact that the unique (unique within a global)
    * id is based on the timestamp. If we later decide to change this logics
    * we will not need to change any other code than this class.
    */
}
