/*------------------------------------------------------------------------------
Name:      ConnectionStateEnum.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;


/**
 * This class simulates an enumeration for the connection states. 
 * <p>
 * Note that this implementation has a fixed number of four states.
 * </p>
 * @author xmlBlaster@marcelruff.info
 */
public final class ConnectionStateEnum implements java.io.Serializable
{
   private static final long serialVersionUID = -8057592644593066934L;

   private final int connectionState;

   private ConnectionStateEnum(int connectionState) {
      this.connectionState = connectionState;
   }

   /**
    * Return a human readable string of the connectionState
    */
   public String toString() {
      switch(this.connectionState) {
         case -1: return "UNDEF";
         case 0:  return "ALIVE";
         case 1:  return "POLLING";
         case 2:  return "DEAD";
         default: return ""+this.connectionState; // error
      }
   }

   /**
    * Returns the int representation of this connectionState
    */
   public final int getInt() {
      return connectionState;
   }

   /**
    * Checks the given int and returns the corresponding ConnectionStateEnum instance. 
    * @param prio For example 7
    * @return The enumeration object for this connectionState
    * @exception IllegalArgumentException if the given connectionState is invalid
    */
   public static final ConnectionStateEnum toConnectionStateEnum(int connectionState) throws IllegalArgumentException {
      if (connectionState < -1 || connectionState > 2) {
         throw new IllegalArgumentException("ConnectionStateEnum: The given connectionState=" + connectionState + " is illegal");
      }
      return connectionStateEnumArr[connectionState];
   }

   /**
    * Parses given string to extract the connectionState of a message. 
    * We are case insensitive (e.g. "POLLING" or "poLLING" are OK). 
    * @param state For example "POLLING"
    * @return The ConnectionStateEnum instance for the message connectionState
    * @exception IllegalArgumentException if the given connectionState is invalid
    */
   public static final ConnectionStateEnum parseConnectionState(String state) throws IllegalArgumentException {
      if (state == null) {
         throw new IllegalArgumentException("ConnectionStateEnum: Given connectionState is null");
      }
      state = state.trim();
      try {
         int connectionState = new Integer(state).intValue();
         return toConnectionStateEnum(connectionState); // may throw IllegalArgumentException
      } catch (NumberFormatException e) {
         state = state.toUpperCase();
         if (state.startsWith("UNDEF"))
            return UNDEF;
         else if (state.startsWith("ALIVE"))
            return ALIVE;
         else if (state.startsWith("POLLING"))
            return POLLING;
         else if (state.startsWith("DEAD"))
            return DEAD;
      }
      throw new IllegalArgumentException("ConnectionStateEnum:  Wrong format of <connectionState>" + state +
                    "</connectionState>, expected one of UNDEF|ALIVE|POLLING|DEAD");
   }

   /**
    * Parses given string to extract the connectionState of a message
    * @param prio For example "polling" or "alive"
    * @param defaultConnectionState Value to use if not parsable
    * @return The ConnectionStateEnum instance for the message connectionState
    */
   public static final ConnectionStateEnum parseConnectionState(String connectionState, ConnectionStateEnum defaultConnectionState) {
      try {
         return parseConnectionState(connectionState);
      }
      catch (IllegalArgumentException e) {
         System.out.println("ConnectionStateEnum: " + e.toString() + ": Setting connectionState to " + defaultConnectionState);
         return defaultConnectionState;
      }
   }

   /**
    * The connection state is not known (-1).
    */
   public static final ConnectionStateEnum UNDEF = new ConnectionStateEnum(-1);

   /**
    * We have a connection (0).
    */
   public static final ConnectionStateEnum ALIVE = new ConnectionStateEnum(0);

   /**
    * We have lost the connection and are polling for it (1).
    */
   public static final ConnectionStateEnum POLLING = new ConnectionStateEnum(1);

   /**
    * The connection is dead an no recovery is possible (2).
    */
   public static final ConnectionStateEnum DEAD = new ConnectionStateEnum(2);

   /**
    * For good performance have a static array of all priorities
    */
   private static final ConnectionStateEnum[] connectionStateEnumArr = {
      UNDEF,
      ALIVE,
      POLLING,
      DEAD
   };
}

