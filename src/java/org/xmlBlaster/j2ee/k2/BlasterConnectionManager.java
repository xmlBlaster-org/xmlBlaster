/*
 * Copyright (c) 2001 Peter Antman Tim <peter.antman@tim.se>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.xmlBlaster.j2ee.k2;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionEvent;
/**
 * The resource adapters own ConnectionManager, used in non managed
 environments. 

 <p>Since this implements is "invisible", pooling is not possible since
 there are no hooks to do thing. This simply means that each connection
 is allways created/closed.</p>

 Will handle some of the houskeeping an appserver nomaly does.

 close();
 cleanup();
 destroy() on error
 
 *
 *
 * Created: Mon Jan 29 22:45:57 2001
 */

public class BlasterConnectionManager implements ConnectionManager{
    
    public BlasterConnectionManager() {
        

    }
    public Object allocateConnection(ManagedConnectionFactory mcf,
                                     ConnectionRequestInfo cxRequestInfo) 
        throws ResourceException{
        ManagedConnection mc = mcf.createManagedConnection(null,cxRequestInfo);
        ConnectionListener l = new ConnectionListener(mc);
        mc.addConnectionEventListener(l);
        return mc.getConnection(null,cxRequestInfo);

    }

   class ConnectionListener implements ConnectionEventListener {
      ManagedConnection mc;
      public ConnectionListener(ManagedConnection mc) {
         this.mc = mc;
      }
      public void connectionClosed(ConnectionEvent event) {
         try {
            mc.cleanup();
            mc.destroy();
         } catch (ResourceException e) {
            System.err.println("Could not close managed connection "+e);
            e.printStackTrace();
         } // end of try-catch

      }

      
      public void connectionErrorOccurred(ConnectionEvent event) {
         try {
            mc.cleanup();
            mc.destroy();
         } catch (ResourceException e) {
            System.err.println("Could not destroy managed connection "+e);
            e.printStackTrace();
         } // end of try-catch
         
      }

      public void localTransactionStarted(ConnectionEvent event) {
         ;//NOOP
      }

      public void localTransactionCommitted(ConnectionEvent event) {
         ;//NOOP
      }

      public void localTransactionRolledback(ConnectionEvent event) {
         ;//NOOP
      }
      
   }

} // BlasterConnectionManager
