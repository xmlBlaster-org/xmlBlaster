/*------------------------------------------------------------------------------
Name:      I_ChangeDetector.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.detector;

import java.util.Map;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.I_ChangeListener;
import org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter;

/**
 * This interface hides the plugin which checks if the data has changed. 
 * <p>
 *There are many ways to detect DB changes, we provide a plugin for MD5 and
 *timestamp checking
 * </p>
<ol>
<li>
   <h2>Changes are delivered by a MOM</h2>
   No DbWatcher is needed: This is the proper approach.
   As legacy systems have various channels which manipulate
   the DB directly it is no solution for older systems.
</li>

<li>
   <h2>Modify/Creation timestamp column</h2>
   A trigger adds the timestamp to each table<br />
   -> This changes the DB schema
</li>

<li>
   <h2>Sequence of primary key</h2>
   Tables which contain a growing sequence of keys may
   use this to detect inserts<br />
   -> Failes for 'update'
</li>

<li>
   <h2>Using a LogMiner</h2>
   -> Is database specific and consumes a lot of CPU/memory
</li>

<li>
   <h2>Audit tables (with trigger)</h2>
   Need to start database in audit mode
   -> How does it work in a portable way?
</li>

<li>
   <h2>Having a trigger for each table</h2>
   The trigger notifies a stored procedure<br />
   -> This needs to change the DB
   -> What if foreign database?
   http://www.oracle.com/technology/oramag/oracle/03-jan/o13java.html
</li>

<li>
   <h2>Flashback queries</h2>
   -> What is this?
</li>

<li>
   <h2>Use ora_rowscan (since Oracle 10g, similar for MS-SQLServer)</h2>
   Example for Oracle (http://www.remote-dba.net/10g_26.htm):<br />
   <tt>select scn_to_timestamp(ora_rowscn) FROM A where ora_rowscn IS NOT null;</tt><br />
   Allows to retrieve the rowchange timestamps<br />
   SQLServer uses the column type <tt>rowVersion</tt>.<br />
   -> Missing for Oracle 9 and other DBs, Oracle 10g and MSQLServer support it.
</li>
   
<li>
   <h2>Using a customized select with MD5</h2>
   Query everything and remember the last state with MD5.
   On change send the complete data again.
</li>
</ol>       
 * @author Marcel Ruff
 */
public interface I_ChangeDetector
{
   /**
    * Needs to be called after construction. 
    * @param info The configuration
    * @param changeListener The listener to notify if something has changed
    * @param dataConverter If not null the data will be transformed immediately during change detection
    * @throws Exception
    */
   void init(I_Info info, I_ChangeListener changeListener, I_DataConverter dataConverter) throws Exception;

    /**
    * Check the observed data for changes.
    * @param attrMap This map can be null. The event source can transport
    *        some specific information to help the changeDetector doing its work
    *        efficiently. 
    * @return > 0 if the observed data has changed. If supported by the plugin
    *         the the number of changed rows,
    * @throws Exception Can be of any type
    */
   int checkAgain(Map attrMap) throws Exception;

   /**
    * Cleanup resources. 
    * @throws Exception Can be of any type
    */
   void shutdown() throws Exception;
}

