/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;


/**
 * Class that starts GUI
 */
public class Main {


  public static void main(String[] args) {
    //get Globals and Log
    Global glob = null;
    LogChannel log = null;
    if (glob == null) glob = new Global().instance();
    log = glob.getLog("jmxGUI");
    // currently commented out due to a deadlock ...
//    SplashWindow sw = new SplashWindow("tims_rainbowfish.gif",null, 5000);
    MainFrame f = new MainFrame(glob);
//    cc.start();
    f.setVisible(true);
//    sw.setVisible(true);

  }
}
