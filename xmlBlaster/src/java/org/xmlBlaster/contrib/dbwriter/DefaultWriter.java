/*------------------------------------------------------------------------------
Name:      Storer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import java.util.Map;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;

public class DefaultWriter implements I_Writer {

   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      System.out.println("=========== NEW BINARY MESSAGE ============\n" + topic + "\n");
   }

   public DefaultWriter() {
      
   }
   
   public void store(DbUpdateInfo info) throws Exception {
      System.out.println("=========== NEW MESSAGE ============\n" + info.toXml("") + "\n");
   }

   public void init(I_Info info) throws Exception {
   }

   public void shutdown() throws Exception {
   }

}
