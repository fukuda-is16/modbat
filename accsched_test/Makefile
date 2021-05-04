#開発用 modbatを修正した時はallを、モデルだけ修正した時はmqtttestを使用
MODBAT_PATH := ..
MODBAT := $(MODBAT_PATH)/build/modbat.jar
OPTION := -n=1 --log-path=./log --log-level=info
MODEL := Test1

all:
	cd $(MODBAT_PATH); ./gradlew build -x test
	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF
	make test
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
