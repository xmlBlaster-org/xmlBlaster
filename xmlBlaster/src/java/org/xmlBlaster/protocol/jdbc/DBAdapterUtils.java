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
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
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

      LogChannel log = Global.instance().getLog("jdbc");
      String columnName = null;
      try {
         int rows = 0;

         //Document doc = new org.apache.crimson.tree.XmlDocument();
         Document doc = Global.instance().getDocumentBuilderFactory().newDocumentBuilder().newDocument();
   
         Element root = doc.createElement(rootnode);
         Element results = doc.createElement("results");
         Element desc = doc.createElement("desc");
   
         root.appendChild(desc);
         root.appendChild(results);
   
         ResultSetMetaData rsmd = rs.getMetaData();
         int               columns = rsmd.getColumnCount();

         Element       numColumns = doc.createElement("numcolumns");
         Text          numColumnsValue = doc.createTextNode("" + columns);

         numColumns.appendChild(numColumnsValue);
         desc.appendChild(numColumns);

         Element columnNames = doc.createElement("columnnames");

         for (int i = 1, j = columns; i <= j; i++) {
            columnName = rsmd.getColumnName(i);
            Element name = doc.createElement("column");
            Text        value = doc.createTextNode(columnName);

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

            Element row = doc.createElement(rownode);

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
               Element col = doc.createElement(columnName);
               CDATASection cvalue = doc.createCDATASection(columnValue);

               col.appendChild(cvalue);
               row.appendChild(col);
               results.appendChild(row);
            }
         }

         Element numRows = doc.createElement("rownum");
         Text        numRowsValue = doc.createTextNode("" + rows);
   
         numRows.appendChild(numRowsValue);
         desc.appendChild(numRows);
   
         doc.appendChild(root);
   
         return doc;

      }
      catch (Exception e) {
         log.warn(ME, "Error in scanning result set for '" + columnName + "': " + e.toString());
         throw new XmlBlasterException(Global.instance(),
                    ErrorCode.INTERNAL_UNKNOWN, ME,
                    "Error in scanning result set for '" + columnName + "'", e);
      }
   }
}
