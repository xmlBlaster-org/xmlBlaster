#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/protocol/corba
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/protocol/corba
OUT_DIR_A = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/protocol/corba/authenticateIdl
OUT_DIR_S = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/protocol/corba/serverIdl
OUT_DIR_C = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/protocol/corba/clientIdl
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
SUB_DIRS = serverIdl clientIdl authenticateIdl
LOCAL_FILES = \
                        $(CLASS_DIR)/AuthServerImpl.class \
                        $(CLASS_DIR)/CorbaDriver.class \
                        $(CLASS_DIR)/CallbackCorbaDriver.class \
                        $(CLASS_DIR)/ServerImpl.class
#------------------------------------------------------------------------------


$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
        $(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)

all:  idl all_SUB_DIRS precond java

precond:
        $(MKDIR_RECURSIVE) $(OUT_DIR_A)
        $(MKDIR_RECURSIVE) $(OUT_DIR_S)
        $(MKDIR_RECURSIVE) $(OUT_DIR_C)
        $(MKDIR_RECURSIVE) $(CLASS_DIR)

idl: $(OUT_DIR_S)/Server.java
        @echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

$(OUT_DIR_S)/Server.java: xmlBlaster.idl
        $(IDL2JAVA)  $(IDL_OUTDIR) $(SRC_JAVA_TOP_DIR) xmlBlaster.idl

java: $(LOCAL_FILES)
        @echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean: clean_SUB_DIRS
        @echo -e "Removing all in $(CLASS_DIR) ..."
        $(RM) $(CLASS_DIR)/*.*
        @echo "Removing all in $(OUT_DIR_S) and $(OUT_DIR_C) ..."
        $(RM) $(OUT_DIR_A)/*Operations.* $(OUT_DIR_A)/*Helper* $(OUT_DIR_A)/*Holder* 
        $(RM) $(OUT_DIR_A)/*Stub.* $(OUT_DIR_A)/*POA*.*
        $(RM) $(OUT_DIR_A)/AuthServer.java
        $(RM) $(OUT_DIR_S)/*Operations.* $(OUT_DIR_S)/*Helper* $(OUT_DIR_S)/*Holder* 
        $(RM) $(OUT_DIR_S)/*Stub.* $(OUT_DIR_S)/*POA*.*
        $(RM) $(OUT_DIR_S)/Server.java $(OUT_DIR_S)/XmlBlasterException.java   $(OUT_DIR_S)/MessageUnit.java
        $(RM) $(OUT_DIR_C)/*Operations.* $(OUT_DIR_C)/*Helper* $(OUT_DIR_C)/*Holder* 
        $(RM) $(OUT_DIR_C)/*Stub.* $(OUT_DIR_C)/*POA*.*
        $(RM) $(OUT_DIR_C)/BlasterCallback.java

xmlBlaster:
        @echo -e "\nStarting xmlBlaster server ...\n"
        ${JAVA_WRAP} ${BlasterSystemProperties} ${BlasterServerMemory} org.xmlBlaster.MainGUI

#doc:
#       $(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java
