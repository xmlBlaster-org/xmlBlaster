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

import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.ejb.EJBException;

import javax.jms.MessageListener;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.jms.JMSException;

import javax.resource.ResourceException;

import org.xmlBlaster.j2ee.k2.client.*;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
/**
 * MessageBeanImpl.java
 *
 *
 * Created: Sat Nov 25 18:07:50 2000
 */

public class JmsAdapter implements MessageDrivenBean, MessageListener{
    private MessageDrivenContext ctx = null;
    private BlasterConnectionFactory factory = null;
    public JmsAdapter() {
        
    }
    public void setMessageDrivenContext(MessageDrivenContext ctx)
        throws EJBException {
        this.ctx = ctx;
        try {
            factory = (BlasterConnectionFactory)new InitialContext ().lookup ("java:comp/env/xmlBlaster");
        } catch (NamingException ex) {
            throw new EJBException ("XmlBlaster not found: "+ex.getMessage ());
        }catch(Throwable th) {
            System.err.println("Throwable: " +th);
            th.printStackTrace();
            throw new EJBException("Throwable in setContext: " +th);
        }

    }
    
    public void ejbCreate() {}

    public void ejbRemove() {ctx=null;}

    public void onMessage(Message message) {

        BlasterConnection con = null;
        try {
            // Get message to handle
            System.err.println("Got message: " + message);

            if (message instanceof TextMessage) {
                 String msg = ((TextMessage)message).getText();

                 // Get connection
                 con = factory.getConnection();
                 
                 // Construct Blaster Headers - howto hanlde key here?
                 String key ="<key oid=\"" + message.getJMSMessageID() +"\" contentMime=\"text/xml\"></key>";
                 String qos = "<qos></qos>";
                 con.publish( new MsgUnit(key,msg.getBytes(),qos));
                 
            } else {
                System.err.println("Got message type I cant handle");
            }
            
        }catch(ResourceException re) {
            System.err.println("Resource ex: " +re);
            re.printStackTrace();
        } catch(XmlBlasterException be) {
            System.err.println("Blaster ex: " +be);
            be.printStackTrace();
        }catch(JMSException je) {
            System.err.println("JMSException ex: " +je);
            je.printStackTrace();
        }catch(Throwable th) {
            System.err.println("Throwable: " +th);
            th.printStackTrace();
            
        }finally {   
            try {
               System.err.println("Jms closing con: " + con);
                if (con != null)
                    con.close ();
            }
            catch (Exception ex) {}
            
        }
    }
} // MessageBeanImpl




