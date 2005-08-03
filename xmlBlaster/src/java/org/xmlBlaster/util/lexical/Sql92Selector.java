/*------------------------------------------------------------------------------
Name:      Sql92Selector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.lexical;
   
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import java.io.StringReader;
import java.util.Map;

public class Sql92Selector implements I_Selector {
   
   private Global global;
   private LogChannel log;
   private Sql92Scanner scanner;
   private Sql92Parser parser;
   
   /**
    * 
    * @param global
    * @param map The map containing the client properties to match against.
    */
   public Sql92Selector(Global global) {
      this.global = global;
      this.log = this.global.getLog("lexical");
      if (this.log.CALL) this.log.call("Sql92Selector", "constructor");
      this.scanner = new Sql92Scanner(global);
      this.parser = new Sql92Parser(this.global, this.scanner);
   }
   
   public boolean select(String query, Map clientProperties) throws XmlBlasterException {
      try {
         if (this.log.CALL) this.log.call("Sql92Selector", "select \"" + query + "\"");
         this.scanner.yyreset(new StringReader(query));
         this.scanner.setClientPropertyMap(clientProperties);
         
         if (this.log.DUMP) {
            return ((Boolean)this.parser.debug_parse().value).booleanValue();      
         }
         return ((Boolean)this.parser.parse().value).booleanValue();      
      }
      catch (Throwable ex) {
         if (this.log.TRACE) {
            ex.printStackTrace();
         }
         throw new XmlBlasterException(this.global,ErrorCode.USER_ILLEGALARGUMENT,
                   "Sql92Selector", "Selector.select: could not interpret the query '" + query + "'", ex);
      }
   }
   
}


