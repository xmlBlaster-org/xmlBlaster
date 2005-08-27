/*------------------------------------------------------------------------------
Name:      Storer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;

public class Storer implements I_Storer {

   public Storer() {
      
   }
   
   public void store(DbUpdateInfo info) throws Exception {
      System.out.println("=========== NEW MESSAGE ============\n" + info.toXml("") + "\n");
   }

   public void init(I_Info info) throws Exception {
   }

   public void shutdown() throws Exception {
   }

}
