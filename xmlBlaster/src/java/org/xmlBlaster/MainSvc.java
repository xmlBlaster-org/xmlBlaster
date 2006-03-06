/*------------------------------------------------------------------------------
Name:      MainSvc.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import com.silveregg.wrapper.WrapperManager;
import com.silveregg.wrapper.WrapperListener;

import java.util.logging.Level;

import org.xmlBlaster.engine.*;
import org.xmlBlaster.util.XmlBlasterException;

public class MainSvc implements WrapperListener 
{
	private Main main = null;
    
    /**************************************************************************
     * WrapperListener Methods
     *************************************************************************/
    public Integer start(String[] args) {
        System.out.println("start()");
        
        final String[] myArgs = args;
        WrapperManager.signalStarting(20000);
        
        Thread startThread = new Thread() {
            public void run() {
                ServerScope glob = new ServerScope(myArgs);
                try {
                    glob.getProperty().set("doBlocking", "false");
                }catch(XmlBlasterException ex) {
                }
                main = new Main(glob);
                while (!main.isHalted()) try { Thread.sleep(60*1000); } catch (Exception e) {}
            }
        };
        startThread.start();
        
        return null;
    }
    
    public int stop(int exitCode) {
        System.out.println("stop(" + exitCode + ")");

	  if (main != null) main.shutdown();
        main = null;
        
        return exitCode;
    }
    
    public void controlEvent(int event) {
        System.out.println("controlEvent(" + event + ")");
        if (event == WrapperManager.WRAPPER_CTRL_C_EVENT) {
            WrapperManager.stop(0);
        }
    }
    
    /**************************************************************************
     * Main Method
     *************************************************************************/
    public static void main(String[] args) {
        System.out.println("Initializing...");
        
        // Start the application.  If the JVM was launched from the native
        //  Wrapper then the application will wait for the native Wrapper to
        //  call the application's start method.  Otherwise the start method
        //  will be called immediately.
        WrapperManager.start(new MainSvc(), args);
    }
}
