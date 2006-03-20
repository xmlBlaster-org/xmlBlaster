/*------------------------------------------------------------------------------
Name:      Query.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding a query string and a prepared query object. 
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.util.Global;

/**
 * Little container which holds a &lt;filter> query string. 
 * For better performance you can preparse the query string and
 * store your query object here as well (see example in GnuRegexFilter).
 * @see org.xmlBlaster.engine.mime.regex.GnuRegexFilter
 */
public final class Query {
   private final String query;
   private Object preparedQuery = null;

   public Query(Global glob, String query) {
      this.query = (query == null) ? "" : query;
   }

   public final String getQuery() {
      return this.query;
   }

   public final void setPreparedQuery(Object preparedQuery) {
      this.preparedQuery = preparedQuery;
   }

   public final Object getPreparedQuery() {
      return this.preparedQuery;
   }

   public final String toString() {
      return this.query;
   }
}
