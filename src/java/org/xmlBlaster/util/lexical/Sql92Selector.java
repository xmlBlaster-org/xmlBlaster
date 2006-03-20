/*------------------------------------------------------------------------------
Name:      Sql92Selector.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.lexical;
   
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import java.io.StringReader;
import java.util.Map;

public class Sql92Selector implements I_Selector {
   
   private Global global;
   private static Logger log = Logger.getLogger(Sql92Selector.class.getName());
   private Sql92Scanner scanner;
   private Sql92Parser parser;
   
   /**
    * 
    * @param global
    * @param map The map containing the client properties to match against.
    */
   public Sql92Selector(Global global) {
      this.global = global;

      if (log.isLoggable(Level.FINER)) this.log.finer("constructor");
      this.scanner = new Sql92Scanner(global);
      this.parser = new Sql92Parser(this.global, this.scanner);
   }
   
   public boolean select(String query, Map clientProperties) throws XmlBlasterException {
      try {
         if (log.isLoggable(Level.FINER)) this.log.finer("select \"" + query + "\"");
         this.scanner.yyreset(new StringReader(query));
         this.scanner.setClientPropertyMap(clientProperties);
         
         if (log.isLoggable(Level.FINEST)) {
            return ((Boolean)this.parser.debug_parse().value).booleanValue();      
         }
         return ((Boolean)this.parser.parse().value).booleanValue();      
      }
      catch (Throwable ex) {
         if (log.isLoggable(Level.FINE)) {
            ex.printStackTrace();
         }
         throw new XmlBlasterException(this.global,ErrorCode.USER_ILLEGALARGUMENT,
                   "Sql92Selector", "Selector.select: could not interpret the query '" + query + "'", ex);
      }
   }
   
}


