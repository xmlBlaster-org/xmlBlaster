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

import java.util.Hashtable;
import java.util.Stack;
/**
 * A pool of mappings from user to pseudo users top allow
 * for more that one "callback" per user.
 
 If an application server instantiates more than one
 ManagedConnectionFactory for XmlBlaster, this will brake.

 I can not make it static in the event that more than one
 XmlBlasterConnector is used whithin one vm wich points to
 different XmlBlaster servers!

 This pool is not used any more.
 *
 *
 * Created: Mon Jan 29 22:15:35 2001
 */

public class PseudoUserPool  {
    Hashtable users = new Hashtable();
    public PseudoUserPool() {
        
    }

    public String popPseudoUser(String name) {
        UserPool up = null;
        synchronized(users) {
            if(users.containsKey(name)) {
                up = (UserPool)users.get(name);
            } else {
                up = new UserPool();
                up.name = name;
                users.put(name,up);
            }
        }
        return up.pop();
    }

    public void push(String name, String pseudoUser) {
        UserPool up = (UserPool)users.get(name);
        if (up != null)
            up.push(pseudoUser);
        // Should never be null
    }

    // Do we need synchronization here - I think not because it
    // will not matter if we miss an n or so. 
    class UserPool {
        int n = 0;
        public String name;
        Stack pseudousers = new Stack();

        public String pop() {
            //String user = null;
            synchronized(pseudousers) {
                if (pseudousers.empty())
                    return name + "_" + Integer.toString(++n);
                else 
                    return (String)pseudousers.pop();
            }
        }

        public void push(String pu) {
            pseudousers.push(pu);
        }
    }
    
} // PseudoUserPool






