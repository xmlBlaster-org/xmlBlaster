/*------------------------------------------------------------------------------
Name:      Main.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;


/**
 * Class that starts GUI
 */
public class Main {

  public static void main(String[] args) {
    //get Globals and Log
    Global glob = new Global(args);

    // currently commented out due to a deadlock ...
//    SplashWindow sw = new SplashWindow("tims_rainbowfish.gif",null, 5000);
    MainFrame f = new MainFrame(glob);
//    cc.start();
    f.setVisible(true);
//    sw.setVisible(true);

  }
}
