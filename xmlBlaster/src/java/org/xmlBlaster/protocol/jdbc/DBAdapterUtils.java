/*
 * ------------------------------------------------------------------------------
 * Name:      DBAdapterUtils.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   Provides utility methods for converting ResultSets to XML
 * Version:   $Id: DBAdapterUtils.java,v 1.9 2002/08/12 13:32:10 ruff Exp $
 * ------------------------------------------------------------------------------
 */
package org.xmlBlaster.protocol.jdbc;

import java.sql.*;
import java.io.*;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Text;

/**
 * Class declaration
 */
public class DBAdapterUtils {
   private static final String ME = "DBAdapterUtils";

   /**
    * Method declaration
    *
    *
    * @param rs
    *
    * @return Document
    */
   public static Document createDocument(ResultSet rs) throws XmlBlasterException
   {
      return createDocument("jdbcresults", "row", -1, rs);
   }

   /**
    * Creates a DOM representation of the result set.
    * @param rootnode
    * @param rownode
    * @param rowlimit
    * @param rs
    * @return The DOM
    */
   public static Document createDocument(String rootnode, String rownode,
                    int rowlimit, ResultSet rs) throws XmlBlasterException {

      LogChannel log = org.xmlBlaster.util.Global.instance().getLog("jdbc");
      int rows = 0;
      Document doc = new org.apache.crimson.tree.XmlDocument();

      Element root = (Element) doc.createElement(rootnode);
      Element results = (Element) doc.createElement("results");
      Element desc = (Element) doc.createElement("desc");

      root.appendChild(desc);
      root.appendChild(results);

      String columnName = null;

      try {
         ResultSetMetaData rsmd = rs.getMetaData();
         int               columns = rsmd.getColumnCount();

         Element       numColumns = (Element) doc.createElement("numcolumns");
         Text          numColumnsValue = (Text) doc.createTextNode("" + columns);

         numColumns.appendChild(numColumnsValue);
         desc.appendChild(numColumns);

         Element columnNames = (Element) doc.createElement("columnnames");

         for (int i = 1, j = columns; i <= j; i++) {
            columnName = rsmd.getColumnName(i);
            Element name = (Element) doc.createElement("column");
            Text        value = (Text) doc.createTextNode(columnName);

            name.appendChild(value);
            columnNames.appendChild(name);
         }

         desc.appendChild(columnNames);

         while (rs.next()) {
            if (log.TRACE) log.trace(ME, "Scanning SQL result with rowlimit=" + rowlimit + ", rows=" + rows);
            if (rowlimit < 0) {
               continue;
            }
            else if (rows >= rowlimit) {
               break;
            }

            rows++;

            Element row = (Element) doc.createElement(rownode);

            for (int i = 1, j = columns; i <= j; i++) {
               int      cType = rsmd.getColumnType(i);
               columnName = rsmd.getColumnName(i);
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

               if (log.TRACE) log.trace(ME, "row="+ rows + ", columnName=" + columnName + ", columnValue='" + columnValue + "'");
               Element col = (Element) doc.createElement(columnName);
               CDATASection cvalue = (CDATASection) doc.createCDATASection(columnValue);

               col.appendChild(cvalue);
               row.appendChild(col);
               results.appendChild(row);
            }
         }
      }
      catch (Exception e) {
         log.error(ME, "Error in scanning result set for '" + columnName + "': " + e.toString());
         throw new XmlBlasterException(ME+".DomResultSetError", "Error in scanning result set for '" + columnName + "': " + e.getMessage());
      }

      Element numRows = (Element) doc.createElement("rownum");
      Text        numRowsValue = (Text) doc.createTextNode("" + rows);

      numRows.appendChild(numRowsValue);
      desc.appendChild(numRows);

      doc.appendChild(root);

      return doc;

   }

}







/*--- formatting done in "xmlBlaster Convention" style on 02-21-2000 ---*/

