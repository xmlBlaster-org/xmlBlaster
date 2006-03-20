/*------------------------------------------------------------------------------
Name:      Storer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;

public class DefaultWriter implements I_Writer {

   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      System.out.println("=========== NEW BINARY MESSAGE ============\n" + topic + "\n");
   }

   public DefaultWriter() {
      
   }
   
   public void store(SqlInfo info) throws Exception {
      System.out.println("=========== NEW MESSAGE ============\n" + info.toXml("") + "\n");
   }

   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      return new HashSet();
   }

   public void init(I_Info info) throws Exception {
   }

   public void shutdown() throws Exception {
   }

}
