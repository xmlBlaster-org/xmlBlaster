#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for idl to Java code generation
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/make/make.h
OUT_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/server
#------------------------------------------------------------------------------


$(OUT_DIR)/Server.java: xmlBlaster.idl
	$(IDL2JAVA)  -d $(SRC_JAVA_TOP_DIR) xmlBlaster.idl

all: precond idl

precond:
	$(MKDIR_RECURSIVE) $(OUT_DIR)

idl: $(OUT_DIR)/Server.java
	@echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean:
	@echo "Removing all in $(OUT_DIR) ..."
	$(RM) $(OUT_DIR)/*Operations.* $(OUT_DIR)/*Helper* $(OUT_DIR)/*Holder* 
	$(RM) $(OUT_DIR)/*Stub.* $(OUT_DIR)/*POA*.*
	$(RM) $(OUT_DIR)/BlasterCallback.java $(OUT_DIR)/Server.java $(OUT_DIR)/XmlBlasterException.java
