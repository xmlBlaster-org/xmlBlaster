
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
package javaclients.j2ee.k2;

import org.xmlBlaster.j2ee.k2.client.*;
import org.xmlBlaster.j2ee.k2.*;

import org.xmlBlaster.util.MsgUnit;
/**
 * TestClient.java
 *
 *
 * Created: Wed Feb  7 21:11:49 2001
 *
 * @author
 * @version
 */

public class TestClient {

    public TestClient() {

    }

    public static void main(String[] args) {
        try {
            BlasterManagedConnectionFactory f = new BlasterManagedConnectionFactory();
            f.setClientProtocol("IOR");
            f.setRmiRegistryPort("1199");
            BlasterConnectionFactory cf = (BlasterConnectionFactory)f.createConnectionFactory();



            BlasterConnection con = cf.getConnection("pra", "passw");

            String key ="<key oid=\"News-35\" contentMime=\"text/xml\">" +
                "<head> <title>Blodiga strider i Kongo-Kinshasa</title></head></key>";
            String qos = "<qos></qos>";
            String msg = "Meddelande";
            con.publish( new MsgUnit(key,msg.getBytes(),qos));

            con.close();


        }catch(Exception ex) {
            System.err.println("Error: " + ex);
            ex.printStackTrace();
        }
    }

} // TestClient
