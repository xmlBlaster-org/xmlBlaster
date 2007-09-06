/*------------------------------------------------------------------------------
Name:      PluginConfigComparator.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import java.util.Comparator;

/**
 * This class is used to compare PluginConfig objects with eachother.
 * <p>
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class PluginConfigComparator implements Comparator
{
   private String ME = "PluginConfigComparator";
   private final Global glob;
   private static Logger log = Logger.getLogger(PluginConfigComparator.class.getName());
   private boolean isAscending = true;

   /**
    * @param glob the global object. A util.Global is sufficient here.
    * @param isAscending 'true' if you want to use this comparator for increasing
    *        (ascending) runlevels, i.e. for startup sequences. 'false' if you want
    *        to use it for descending (shutdown) sequences.
    */
   public PluginConfigComparator(Global glob, boolean isAscending) {
      this.isAscending = isAscending;
      this.glob = glob;

      if (log.isLoggable(Level.FINER)) this.log.finer("constructor");
   }


   /**
    * Compares its two arguments for order. Returns a negative integer, zero, or a 
    * positive integer as the first argument is less than, equal to, or greater 
    * than the second. Null objects are considered of different type and therefore 
    * will throw a runtime exception (ClassCastException).
    */
   public final int compare(Object o1, Object o2) {
      if (!(o1 instanceof PluginConfig) || !(o2 instanceof PluginConfig)) {
         String o1Txt = "null", o2Txt = "null";
         if (o1 != null) o1Txt = o1.toString();
         if (o2 != null) o2Txt = o2.toString();
         throw new ClassCastException(ME + " comparison between '" + o1Txt + "' and '" + o2Txt + "' is not possible because wrong types");
      }
      PluginConfig p1 = (PluginConfig)o1;
      PluginConfig p2 = (PluginConfig)o2;
      RunLevelAction action1 = null, action2 = null;
      if (this.isAscending) {
         action1 = p1.getUpAction();
         action2 = p2.getUpAction();
         int diff = action1.getOnStartupRunlevel() - action2.getOnStartupRunlevel();
         if (diff != 0) return diff;
         diff = action1.getSequence() - action2.getSequence();
         if (diff != 0) return diff;
         return p1.uniqueTimestamp.compareTo(p2.uniqueTimestamp);
      }
      else {
         action1 = p1.getDownAction();
         action2 = p2.getDownAction();
         int diff = action2.getOnShutdownRunlevel() - action1.getOnShutdownRunlevel();
         if (diff != 0) return diff;
         diff = action1.getSequence() - action2.getSequence();
         if (diff != 0) return diff;
         return p2.uniqueTimestamp.compareTo(p1.uniqueTimestamp);
      }
   }

   /**
    * Indicates whether some other object is "equal to" this Comparator.
    */
   public boolean equals(Object obj) {
      return this == obj;
   }

   
}
