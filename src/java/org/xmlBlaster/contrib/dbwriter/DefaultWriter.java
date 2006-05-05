/*------------------------------------------------------------------------------
Name:      Storer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;

public class DefaultWriter implements I_Writer {

   Logger log = Logger.getLogger(DefaultWriter.class.getName());
   
   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      log.info("=========== NEW BINARY MESSAGE ============\n" + topic + "\n");
   }

   public DefaultWriter() {
      
   }
   
   public void store(SqlInfo info) throws Exception {
      log.info("=========== NEW MESSAGE ============\n" + info.toXml("") + "\n");
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
   
   public synchronized void registerListener(I_Update update) throws Exception {
   }

   public synchronized void unregisterListener(I_Update update) throws Exception {
   }
    
}
