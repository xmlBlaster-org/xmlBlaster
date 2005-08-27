/*------------------------------------------------------------------------------
Name:      DbUpdateInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.xmlBlaster.util.def.Constants;

public class DbUpdateInfo {

   public final static String SQL_TAG = "sql";

   private DbUpdateInfoDescription description;
   
   private List rows;
   
   
   public DbUpdateInfo() {
      this.rows = new ArrayList();
   }


   public DbUpdateInfoDescription getDescription() {
      return this.description;
   }

   public void setDescription(DbUpdateInfoDescription description) {
      this.description = description;
   }


   public List getRows() {
      return this.rows;
   }
   
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<").append(SQL_TAG).append(">");

      sb.append(this.description.toXml(extraOffset + "  "));

      
      Iterator iter = this.rows.iterator();
      while (iter.hasNext()) {
         DbUpdateInfoRow recordRow = (DbUpdateInfoRow)iter.next();
         sb.append(recordRow.toXml(extraOffset + "  "));
      }
      sb.append(offset).append("</").append(SQL_TAG).append(">");
      return sb.toString();
   }
   
}
