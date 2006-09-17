/*------------------------------------------------------------------------------
Name:      RunLevelAction.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;


/**
 * This class contains the information on how and when a certain plugin is invoked by the run level manager
 * <p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 * <pre>
 *  &lt;action do='LOAD'
 *             onStartupRunlevel='3'
 *             sequence='5'
 *             onFail='RESOURCE_CONFIGURATION_PLUGINFAILED'/>
 * </pre>
 */
public class RunLevelAction
{
   public final static String LOAD = "LOAD";
   public final static String STOP = "STOP";

   private final Global glob;
   private static Logger log = Logger.getLogger(RunLevelAction.class.getName());

   /* the action to trigger (either LOAD or STOP) */
   private String action;

   /* the run level when going up */
   private int upLevel = -1;

   /* the run level when going down */
   private int downLevel = -1;

   /* the error code to return in case of an error. If null, no error will be returned */
   private ErrorCode errorCode;

   /* the runlevel internal sequence number at which this action will be invoked */
   private int sequence = 0;


   /**
    * This constructor takes all parameters needed
    */
   public RunLevelAction(Global glob, String action, int upLevel, int downLevel,
      ErrorCode errorCode, int sequence) {

      if (log.isLoggable(Level.FINER)) log.finer("constructor");
      this.glob = glob;
      this.action = action;
      this.upLevel  = upLevel;
      this.downLevel = downLevel;
      this.errorCode = errorCode;
      this.sequence = sequence;
   }

   /**
    * This constructor is the minimal constructor.
    */
   public RunLevelAction(Global glob) {
      this(glob, LOAD, -1, -1, null, 0);
   }

   /**
    * returns a clone of this object.
    */
   public Object clone() {
      return new RunLevelAction(this.glob, this.action, this.upLevel,
                                this.downLevel, this.errorCode, this.sequence);
   }

   public String getDo() {
      return this.action;
   }

   public void setDo(String action) {
      this.action = action;
   }

   public int getOnStartupRunlevel() {
      return this.upLevel;
   }

   public void setOnStartupRunlevel(int upLevel) {
      this.upLevel = upLevel;
   }

   public boolean isOnStartupRunlevel() {
     return this.upLevel > 0;
   }

   public int getOnShutdownRunlevel() {
      return this.downLevel;
   }

   public void setOnShutdownRunlevel(int downLevel) {
      this.downLevel = downLevel;
   }

   public boolean isOnShutdownRunlevel() {
     return this.downLevel > 0;
   }

   public ErrorCode getOnFail() {
      return this.errorCode;
   }

   public void setOnFail(ErrorCode errorCode) {
      this.errorCode = errorCode;
   }

   public boolean hasOnFail() {
      return this.errorCode != null;
   }

   public int getSequence() {
      return this.sequence;
   }

   public void setSequence(int sequence) {
      this.sequence = sequence;
   }

   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(512);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<action ");
      sb.append("do='").append(this.action).append("' ");
      if (this.upLevel > -1)
         sb.append("onStartupRunlevel='").append(this.upLevel).append("' ");
      if (this.downLevel > -1)
         sb.append("onShutdownRunlevel='").append(this.downLevel).append("' ");
      if (this.errorCode != null ) {
         sb.append("onFail='").append(this.errorCode.getErrorCode()).append("' ");
      }
      if (this.sequence > 0) { // zero is default and therefore not written ...
         sb.append("sequence='").append(this.sequence).append("' ");
      }
      sb.append("/>");
      return sb.toString();
   }

   public String toXml() {
      return toXml("");
   }
}
