/*
 * (C) Copyright IBM Corp. 1999  All rights reserved.
 *
 * US Government Users Restricted Rights Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 *
 * The program is provided "as is" without any warranty express or
 * implied, including the warranty of non-infringement and the implied
 * warranties of merchantibility and fitness for a particular purpose.
 * IBM will not be liable for any damages suffered by you as a result
 * of using the Program. In no event will IBM be liable for any
 * special, indirect or consequential damages or lost profits even if
 * IBM has been advised of the possibility of their occurrence. IBM
 * will not be liable for any third party claims against you.
 */

package testsuite.sax;
                    
import org.xml.sax.AttributeList;

/**
 * An AttributeList implementation that can perform more operations
 * than the attribute list helper supplied with the standard SAX
 * distribution.
 */
public class AttributeListImpl
    implements AttributeList {

    //
    // Data
    //

    /** Head node. */
    private ListNode head;

    /** Tail node. */
    private ListNode tail;

    /** Length. */
    private int length;

    //
    // AttributeList methods
    //

    /** Returns the number of attributes. */
    public int getLength() {
        return length;
    }

    /** Returns the attribute name by index. */
    public String getName(int index) {

        ListNode node = getNodeAt(index);
        return (node != null) ? node.name : null;

    } // getName(int):String

    /** Returns the attribute type by index. */
    public String getType(int index) {

        ListNode node = getNodeAt(index);
        return (node != null) ? node.type : null;

    } // getType(int):String

    /** Returns the attribute value by index. */
    public String getValue(int index) {

        ListNode node = getNodeAt(index);
        return (node != null) ? node.value : null;

    } // getType(int):String

    /** Returns the attribute type by name. */
    public String getType(String name) {

        ListNode node = getNodeAt(name);
        return (node != null) ? node.type : null;

    } // getType(int):String

    /** Returns the attribute value by name. */
    public String getValue(String name) {

        ListNode node = getNodeAt(name);
        return (node != null) ? node.value : null;

    } // getType(int):String

    //
    // Public methods
    //

    /** Adds an attribute. */
    public void addAttribute(String name, String type, String value) {

        ListNode node = new ListNode(name, type, value);
        if (length == 0) {
            head = node;
        }
        else {
            tail.next = node;
        }
        tail = node;
        length++;

    } // addAttribute(String,String,String)

    /** Inserts an attribute. */
    public void insertAttributeAt(int index, 
                                  String name, String type, String value) {

        // if list is empty, add attribute
        if (length == 0 || index >= length) {
            addAttribute(name, type, value);
            return;
        }

        // insert at beginning of list
        ListNode node = new ListNode(name, type, value);
        if (index < 1) {
            node.next = head;
            head = node;
        }
        else {
            ListNode prev = getNodeAt(index - 1);
            node.next = prev.next;
            prev.next = node;
        }
        length++;

    } // addAttribute(String,String,String)

    /** Removes an attribute. */
    public void removeAttributeAt(int index) {

        if (length == 0) {
            return;
        }

        if (index == 0) {
            head = head.next;
            if (head == null) {
                tail = null;
            }
            length--;
        }
        else {
            ListNode prev = getNodeAt(index - 1);
            ListNode node = getNodeAt(index);
            if (node != null) {
                prev.next = node.next;
                if (node == tail) {
                    tail = prev;
                }
                length--;
            }
        }

    } // removeAttributeAt(int)

    //
    // Private methods
    //

    /** Returns the node at the specified index. */
    private ListNode getNodeAt(int i) {

        for (ListNode place = head; place != null; place = place.next) {
            if (--i == -1) {
                return place;
            }
        }

        return null;

    } // getNodeAt(int):ListNode

    /** Returns the first node with the specified name. */
    private ListNode getNodeAt(String name) {

        if (name != null) {
            for (ListNode place = head; place != null; place = place.next) {
                if (place.name.equals(name)) {
                    return place;
                }
            }
        }

        return null;

    } // getNodeAt(int):ListNode

    //
    // Object methods
    //

    /** Returns a string representation of this object. */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append('[');
        str.append("len=");
        str.append(length);
        str.append(", {");
        for (ListNode place = head; place != null; place = place.next) {
            str.append(place.name);
            if (place.next != null) {
                str.append(", ");
            }
        }
        str.append("}]");

        return str.toString();

    } // toString():String

    //
    // Classes
    //

    /**
     * An attribute node.
     */
    static class ListNode {

        //
        // Data
        //

        /** Attribute name. */
        public String name;

        /** Attribute type. */
        public String type;

        /** Attribute value. */
        public String value;

        /** Next node. */
        public ListNode next;

        //
        // Constructors
        //

        /** Default constructor. */
        public ListNode(String name, String type, String value) {
            this.name  = name;
            this.type  = type;
            this.value = value;
        }

    } // class ListNode

} // class AttributeListImpl
