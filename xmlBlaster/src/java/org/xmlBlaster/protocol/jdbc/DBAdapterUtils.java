/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/

/*
 * ------------------------------------------------------------------------------
 * Name:      DBAdapterUtils.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   Provides utility methods for converting ResultSets to XML
 * Version:   $Id: DBAdapterUtils.java,v 1.3 2000/06/18 15:22:01 ruff Exp $
 * ------------------------------------------------------------------------------
 */

package org.xmlBlaster.protocol.jdbc;

import java.sql.*;
import java.io.*;
import com.sun.xml.tree.*;
import org.w3c.dom.*;
import org.jutils.log.Log;

/**
 * Class declaration
 *
 *
 * @author
 * @version %I%, %G%
 */
public class DBAdapterUtils {
   private static final String ME = "DBAdapterUtils";

   /**
    * Method declaration
    *
    *
    * @param rs
    *
    * @return
    *
    * @see
    */
   public static XmlDocument createDocument(ResultSet rs) {
      return createDocument("jdbcresults", "row", -1, rs);
   }

   /**
    * Method declaration
    *
    *
    * @param rootnode
    * @param rownode
    * @param rowlimit
    * @param rs
    *
    * @return
    *
    * @see
    */
   public static XmlDocument createDocument(String rootnode, String rownode,
                                            int rowlimit, ResultSet rs) {

      int rows = 0;
      XmlDocument doc = new XmlDocument();

      ElementNode root = (ElementNode) doc.createElement(rootnode);
      ElementNode results = (ElementNode) doc.createElement("results");
      ElementNode desc = (ElementNode) doc.createElement("desc");

      root.appendChild(desc);
      root.appendChild(results);

      try {
         ResultSetMetaData rsmd = rs.getMetaData();
         int               columns = rsmd.getColumnCount();

         ElementNode       numColumns =
            (ElementNode) doc.createElement("numcolumns");
         Text              numColumnsValue = (Text) doc.createTextNode(""
                 + columns);

         numColumns.appendChild(numColumnsValue);
         desc.appendChild(numColumns);

         ElementNode columnNames =
            (ElementNode) doc.createElement("columnnames");

         for (int i = 1, j = columns; i <= j; i++) {
            String      columnName = rsmd.getColumnName(i);
            ElementNode name = (ElementNode) doc.createElement("column");
            Text        value = (Text) doc.createTextNode(columnName);

            name.appendChild(value);
            columnNames.appendChild(name);
         }

         desc.appendChild(columnNames);

         while (rs.next()) {
            if (Log.TRACE) Log.trace(ME, "Scanning SQL result with rowlimit=" + rowlimit + ", rows=" + rows);
            if (rowlimit < 0) {
               continue;
            }
            else if (rows >= rowlimit) {
               break;
            }

            rows++;

            ElementNode row = (ElementNode) doc.createElement(rownode);

            for (int i = 1, j = columns; i <= j; i++) {
               int      cType = rsmd.getColumnType(i);
               String   columnName = rsmd.getColumnName(i);
               String   columnValue = "";

               switch (cType) {

               case Types.VARCHAR:
                  columnValue = rs.getString(i);

                  break;

               case Types.DOUBLE:
                  columnValue = "" + rs.getDouble(i);

                  break;

               case Types.FLOAT:
                  columnValue = "" + rs.getFloat(i);

                  break;

               case Types.INTEGER:
                  columnValue = "" + rs.getInt(i);

                  break;

               case Types.NUMERIC:
                  columnValue = "" + rs.getLong(i);

                  break;

               case Types.DATE:
                  columnValue = rs.getDate(i).toString();

                  break;

               case Types.TIMESTAMP:
                  columnValue = rs.getTimestamp(i).toString();

                  break;
               }

               if (Log.TRACE) Log.trace(ME, "row="+ rows + ", columnName=" + columnName + ", columnValue='" + columnValue + "'");
               ElementNode col = (ElementNode) doc.createElement(columnName);
               Text        cvalue = (Text) doc.createTextNode(columnValue);

               col.appendChild(cvalue);
               row.appendChild(col);
               results.appendChild(row);
            }
         }
      }
      catch (Exception e) {
         Log.error(ME, "Error in scanning result set: " + e.toString());
         // !!!!! throw XmlBlasterException
      }

      ElementNode numRows = (ElementNode) doc.createElement("rownum");
      Text        numRowsValue = (Text) doc.createTextNode("" + rows);

      numRows.appendChild(numRowsValue);
      desc.appendChild(numRows);

      doc.appendChild(root);

      return doc;

   }

}







/*--- formatting done in "xmlBlaster Convention" style on 02-21-2000 ---*/

