CPP_DIR=$(XMLBLASTER_HOME)/src/c++

include $(CPP_DIR)/Makefile.def

EVERYTHING= $(BIN_DIR)/TestGet \
	 $(BIN_DIR)/TestSub \
	 $(BIN_DIR)/RamTest \
	 $(BIN_DIR)/TestLogin \
	 $(BIN_DIR)/clientPOA

#    $(BIN_DIR)/clientBOA 

ifeq ($(CORBA_CPP),mico)
ALL=$(EVERYTHING) $(BIN_DIR)/clientPOA	
else
ALL=$(EVERYTHING)
endif


.cc.o:
	$(CC) $(CORBA_INC) $(XMLCPP_INC) -I$(CPP_DIR) -c $<

all: bin $(ALL)

test: all
	./test.sh

bin:
	$(MKDIR) bin

$(BIN_DIR)/TestGet: TestGet.o
ifeq ($(USE_CPP),true) 
	$(CC) TestGet.o $(CLIENT_OBJ) $(CORBA_OBJ) -o$(BIN_DIR)/TestGet $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -lstdc++
endif

$(BIN_DIR)/TestSub: TestSub.o
ifeq ($(USE_CPP),true) 
	$(CC) TestSub.o $(CLIENT_OBJ) $(CORBA_OBJ) -o$(BIN_DIR)/TestSub $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -lstdc++
endif

$(BIN_DIR)/RamTest: RamTest.o
ifeq ($(USE_CPP),true) 
	$(CC) RamTest.o $(CLIENT_OBJ) $(CORBA_OBJ) -o$(BIN_DIR)/RamTest $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -lstdc++
endif

$(BIN_DIR)/TestLogin: TestLogin.o
ifeq ($(USE_CPP),true) 
	$(CC) TestLogin.o $(CLIENT_OBJ) $(CORBA_OBJ) -o$(BIN_DIR)/TestLogin $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -lstdc++
endif

$(BIN_DIR)/clientPOA: clientPOA.o
ifeq ($(USE_CPP),true) 
	$(CC) clientPOA.o $(CLIENT_OBJ) $(CORBA_OBJ) -o$(BIN_DIR)/clientPOA $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -lstdc++
endif

# Does not compile ruff@swand.lake.de 2001-09-03
#$(BIN_DIR)/clientBOA: clientBOA.o
#ifeq ($(USE_CPP),true) 
#ifeq ($(CORBA_CPP),mico)
#	$(CC) clientBOA.o $(CLIENT_OBJ) $(CORBA_OBJ) -o$(BIN_DIR)/clientBOA $(XMLCPP_LIB) $(CORBA_LIB) $(COS_LIB) -lstdc++
#endif
#endif

clean: 
	$(RM) *.o $(ALL)
