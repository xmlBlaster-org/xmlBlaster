/*------------------------------------------------------------------------------
Name:      FilterRule.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding the query string. 
Version:   $Id: FilterRule.java,v 1.1 2002/03/14 17:21:46 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * Contains the filter rule string as sent on subscribe()
 */
public class FilterRule
{
   private String ME = "FilterRule";
   private String query;
   private Global glob;
   private Log log;

   /**
    */
   public FilterRule(Global glob, String query)
   {
      this.glob = glob;
      this.log = glob.getLog();
      this.query = query;
   }

   public void finalize()
   {
      if (Log.TRACE) Log.trace(ME, "finalize - garbage collected");
   }

   public final String getQuery()
   {
      return this.query;
   }

   public final Global getGlobal()
   {
      return this.glob;
   }
}

