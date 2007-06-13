/*------------------------------------------------------------------------------
Name:      I_PrePostStatement.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;

import java.sql.Connection;

import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.replication.ReplicationConstants;

/**
 * I_PrePostStatement is an interface which is invoked just before a statement is invoked for a row and just after. 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_PrePostStatement extends I_ContribPlugin, ReplicationConstants {

   /**
    * This method is invoked in the ReplicationWriter just before an INSERT, UPDATE or DELETE and CREATE,
    * ALTER, DROP is invoked on the specified entry (the row). The user can manipulate the row, the sqlInfo 
    * or can perform some operations on the database. Note that the row has its state which is already modified 
    * when it comes to catalog, schema and table done by the mappers, but the row modification has not been done yet.
    * 
    * @param operation is the operation to be invoked for the statement. It can be one of the ReplicationConstants
    *        defined actions INSERT, UPDATE DELETE, CREATE, ALTER or DROP. 
    * @param conn The Database connection object. The implementor is not responsible for cleaning up the resource,
    *             this is done by the invoker.
    * @param sqlInfo The object of the whole unmodified message coming from the source.
    * @param tableDescription the description (metadata) of the table from which the current table is coming from,
    *        if the row would contain entries (columns) coming from more than one table, then null is passed. The
    *        ReplicationWriter could also pass null if the operation is a CREATE and the table does not exist yet 
    *        in the replica database. As this is created out of the metadata information of the destination, the
    *        catalog-, schema-, table- and column names are already the ones after any mapping modification. 
    * @param currentRow The row which currently will be processed. It can be null if the operation is a CREATE,
    *        ALTER or DROP. The columns specified in this row are already modified according to the mapping.
    * 
    * @return true if the statement has to be executed, false otherwise (then the statement is not executed). If 
    *        it returns false, then the postStatement is not executed either.
    * @throws Exception thrown if something went wrong in the implementation. If it is thrown, then the operation is
    *        not performed and a rollback of the entire transaction is done.
    */
   boolean preStatement(String operation, Connection conn, SqlInfo info, SqlDescription tableDescription, SqlRow currentRow) throws Exception;

   /**
    * This method is invoked in the ReplicationWriter just after an INSERT, UPDATE, DELETE, CREATE, ALTER, DROP 
    * is invoked on the specified entry (the row). The user can manipulate the row, the sqlInfo or can perform 
    * some operations on the database. Note that the row has its state which is after any modification done by 
    * the mappers, i.e. its content corresponds to what the writer will write, not necessarly whatt the watcher 
    * has sent. This method is only invoked if preStatement returned true.
    * 
    * @param operation is the operation to be invoked for the statement. It can be one of the ReplicationConstants
    *        defined actions INSERT, UPDATE or DELETE, CREATE, ALTER or DROP.
    * @param conn The Database connection object. The implementor is not responsible for cleaning up the resource,
    *         this is done by the invoker. 
    * @param sqlInfo The object of the whole unmodified message coming from the source.
    * @param tableDescription the description (metadata) of the table from which the current table is coming from,
    *        if the row would contain entries (columns) coming from more than one table, then null is passed. The
    *        ReplicationWriter could also pass null if the operation is a CREATE and the table does not exist yet 
    *        in the replica database. As this is created out of the metadata information of the destination, the
    *        catalog-, schema-, table- and column names are already the ones after any mapping modification. 
    * @param currentRow The row which currently will be processed. It can be null if the operation is a CREATE,
    *        ALTER or DROP. The columns specified in this row are already modified according to the mapping.
    * 
    * @throws Exception thrown if something went wrong in the implementation. If it is thrown, then a rollback of 
    *          the entire transaction is done.
    */
   void postStatement(String operation, Connection conn, SqlInfo info, SqlDescription tableDescription, SqlRow currentRow) throws Exception;
   
}
