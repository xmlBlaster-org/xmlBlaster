/*------------------------------------------------------------------------------
Name:      I_Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster;


/**
 * I_Main interface is a minimized interface to control Main.java.
 * <p />
 * It allows instantiating xmlBlaster in EmbeddedXmlBlaster with a specific classloader.
 */
public interface I_Main
{
   public void init(org.xmlBlaster.util.Global g);
   public void init(java.util.Properties p);
   public void shutdown();
   public boolean isHalted();
   public org.xmlBlaster.engine.Global getGlobal();
}
