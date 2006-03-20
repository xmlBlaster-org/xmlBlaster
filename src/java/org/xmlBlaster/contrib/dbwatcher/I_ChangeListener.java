/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher;

import java.sql.Connection;

/**
 * Interface which reports back changes to the observed data. 
 *
 * The change can be a pure, empty event or it can contain all details already
 * depending on the implementing notifier. 
 *
 * @author Marcel Ruff
 */
public interface I_ChangeListener extends java.util.EventListener
{
   /**
    * Is called for every data source changed. 
    * @param changeEvent Transports all needed information about the change
    */
   void hasChanged(ChangeEvent changeEvent);

   /**
    * Does a SQL query with <tt>stmt</tt> and sends all ResultSets to {@link I_ChangePublisher}. 
    * <p>
    * The messages are formatted by the configured {@link I_DataConverter} plugin.
    * </p> 
    * <p>
    * Note: This is a common convenienve function, in a future refactoring we
    * should have this method in a separate interface.
    * </p>
    * @param stmt The SQL query string (with/without grouping)
    * @param useGroupCol If true we send a message for each
    *                    {@link ChangeEvent#getGroupColName} change
    * @param changeEvent The reason to send
    * @param conn The JDBC connection, if null we get one ourself in auto commit mode
    * @return The number of changes detected TODO! (currently returns -1) 
    * @throws Exception of any type
    */
   int publishMessagesFromStmt(final String stmt, final boolean useGroupCol,
                               final ChangeEvent changeEvent,
                               Connection conn) throws Exception;
   
   /**
    * Cleanup resources. 
    * @throws Exception Can be of any type as implemented by plugin 
    */
   void shutdown() throws Exception;
}
