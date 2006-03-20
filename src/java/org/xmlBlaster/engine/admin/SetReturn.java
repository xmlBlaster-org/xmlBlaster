/*------------------------------------------------------------------------------
Name:      SetReturn.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains informations for set() return
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

/**
 * Holds some data of interest, is returned by set() invocations from CommandManager. 
 * <p />
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 */
public final class SetReturn
{
   public CommandWrapper commandWrapper;
   public String returnString;
   public SetReturn(CommandWrapper commandWrapper, String returnString) {
      this.commandWrapper = commandWrapper;
      this.returnString = returnString;
   } 
}
