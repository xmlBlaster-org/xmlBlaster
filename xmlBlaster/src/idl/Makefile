#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for idl to Java code generation
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
OUT_DIR_A = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/authenticateIdl
OUT_DIR_S = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/serverIdl
OUT_DIR_C = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/clientIdl
#------------------------------------------------------------------------------


$(OUT_DIR_S)/Server.java: xmlBlaster.idl
	$(IDL2JAVA)  -d $(SRC_JAVA_TOP_DIR) xmlBlaster.idl

all: precond idl

precond:
	$(MKDIR_RECURSIVE) $(OUT_DIR_S)
	$(MKDIR_RECURSIVE) $(OUT_DIR_C)

idl: $(OUT_DIR_S)/Server.java
	@echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean:
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
