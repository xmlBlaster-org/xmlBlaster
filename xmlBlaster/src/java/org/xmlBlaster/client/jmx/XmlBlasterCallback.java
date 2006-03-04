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
import java.util.logging.Logger;
import java.util.logging.Level;

public class XmlBlasterCallback implements Callback{

  private int status = XmlBlasterConnector.UNKNOWN;
  private static String ME = "XmlBlasterCallback";
  private String ID = null;
  private MethodInvocation mi = null;
  private Global glob;
   private static Logger log = Logger.getLogger(XmlBlasterCallback.class.getName());

  XmlBlasterCallback(String ID, MethodInvocation mi) {
    status = XmlBlasterConnector.SENDING;

    this.ID = ID;
    this.mi = mi;
    this.glob = Global.instance();

  }

  protected void setMethodInvocation(MethodInvocation mi) {

    synchronized (this) {
      if (log.isLoggable(Level.FINER)) this.log.finer("setMethodInvocation for " + mi.getMethodName());
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
      if (log.isLoggable(Level.FINER)) this.log.finer("get");
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
