/*------------------------------------------------------------------------------
Name:      XmlBlasterCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import java.rmi.RemoteException;
import org.xmlBlaster.util.admin.extern.MethodInvocation;

import org.xmlBlaster.util.admin.extern.XmlBlasterConnector;
import org.xmlBlaster.util.Global;
import org.jutils.log.LogChannel;

public class XmlBlasterCallback implements Callback{

  private int status = XmlBlasterConnector.UNKNOWN;
  private static String ME = "XmlBlasterCallback";
  private String ID = null;
  private MethodInvocation mi = null;
  private Global glob;
  private LogChannel log;

  XmlBlasterCallback(String ID, MethodInvocation mi) {
    status = XmlBlasterConnector.SENDING;

    this.ID = ID;
    this.mi = mi;
    this.glob = Global.instance();
    this.log = this.glob.getLog("jmxGUI");
  }

  protected void setMethodInvocation(MethodInvocation mi) {

    synchronized (this) {
      if (this.log.CALL) this.log.call(ME, "setMethodInvocation for " + mi.getMethodName());
      this.mi = mi;
      status = XmlBlasterConnector.FINISHED;
      notifyAll();
    }
  }

  public int peek() {
    return status;
  }

  public Object get() throws RemoteException {
    synchronized (this) {
      if (this.log.CALL) this.log.call(ME, "get");
      while (status != XmlBlasterConnector.FINISHED) {
        try {
          wait();
        }
        catch (InterruptedException e) {}
        }
      }
      return mi.getReturnValue();
    }
  }
