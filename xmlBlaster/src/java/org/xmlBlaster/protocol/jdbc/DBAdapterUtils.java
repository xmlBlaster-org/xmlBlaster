/*
 * ------------------------------------------------------------------------------
 * Name:      DBAdapterUtils.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   Provides utility methods for converting ResultSets to XML
 * Version:   $Id$
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
   private final static String NULL_STR = "NULL";

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

               case Types.CHAR:
               case Types.VARCHAR:
               case Types.LONGVARCHAR:
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
                  Date d = rs.getDate(i);
                  columnValue = (d==null) ? NULL_STR : d.toString();

                  break;

               case Types.TIMESTAMP:
                  Timestamp t = rs.getTimestamp(i);
                  columnValue = (t==null) ? NULL_STR : t.toString();

                  break;

               case Types.BIT:
               case Types.TINYINT:
               case Types.SMALLINT:
               case Types.BIGINT:
               case Types.REAL:
               case Types.DECIMAL:
               case Types.TIME:
               case Types.BINARY:
               case Types.VARBINARY:
               case Types.LONGVARBINARY:
               case Types.NULL:
               case Types.OTHER:
               case Types.JAVA_OBJECT:
               case Types.DISTINCT:
               case Types.STRUCT:
               case Types.ARRAY:
               case Types.BLOB:
               case Types.CLOB:
               case Types.REF:
               /* since JDK 1.4
               case Types.DATALINK:
               case Types.BOOLEAN:
               */
                  Object o1 = rs.getObject(i);
                  columnValue = (o1==null) ? NULL_STR : o1.toString();
                  break;

               default:
                  if (log.TRACE) log.warn(ME, "Datatype '" + cType + "' of column '" + columnName + "' is not implemented, plase add a case statement in DBAdapterUtils.java");
                  Object o2 = rs.getObject(i);
                  columnValue = (o2==null) ? NULL_STR : o2.toString();
                  break;
               }

               if (log.TRACE) log.trace(ME, "row="+ rows + ", columnName=" + columnName + ", type=" + cType + " columnValue='" + columnValue + "'");
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

