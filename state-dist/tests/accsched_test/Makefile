#開発用 modbatを修正した時はallを、モデルだけ修正した時はmqtttestを使用
MODBAT_PATH := ../../..
LOG_PATH ?= log
MODBAT := $(MODBAT_PATH)/build/modbat.jar
# グローバル変数を用いていて複数回想定ではないので、n=1にしている。
OPTION1 := -n=1 --log-level=info
OPTION := $(OPTION1) --log-path=./log
MODELS := Test1 Test2 Test3 Test4 Test5 Test6 GuardTest WeightTest WeightTest2

all: $(MODELS)
$(MODELS): MODEL=$@
$(MODELS): LP=$(LOG_PATH)/$@
$(MODELS):
	make test MODEL=$@
	mkdir -p $(LP)
	rm $(LP)/*.log -f
	rm $(LP)/*.err -f
	scala -cp .:$(MODBAT) $(MODBAT) $(OPTION1) --log-path=$(LP) Test.$(MODEL)



# all:
# 	cd $(MODBAT_PATH); ./gradlew build -x test
# 	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF
# 	make test
modbat:
	cd $(MODBAT_PATH); ./gradlew build -x test
	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF
test: $(MODBAT) $(MODEL).scala
	scalac -cp $(MODBAT) $(MODEL).scala

.PHONY: run cleanlog
run:
	mkdir -p log
	@make cleanlog
	scala -cp .:$(MODBAT) $(MODBAT) $(OPTION) Test.$(MODEL)
cleanlog:
	find -name "*.log" | xargs rm -f
	find -name "*.err" | xargs rm -f
clean:
	make cleanlog
	find -name "*.class" | xargs rm -f
	find -name "paho*" | xargs rm -rf
