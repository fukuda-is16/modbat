MODBAT_PATH := ../..
MODBAT := $(MODBAT_PATH)/build/modbat.jar
OPTION := -n=1 --log-path=./log --log-level=info
MODEL := Test2

test_: $(MODBAT) $(MODEL).scala
	#scalac -classpath .:$(MODBAT) Test.scala Spot.scala Drone.scala Controller.scala
	scalac -classpath .:$(MODBAT) Test2.scala



all:
	cd $(MODBAT_PATH); ./gradlew build -x test
	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF
	make test
modbat:
	cd $(MODBAT_PATH); ./gradlew build -x test
	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF

.PHONY: run cleanlog
run:
	mkdir -p log
	make cleanlog
	#scala -classpath .:$(MODBAT) $(MODBAT) $(OPTION) $(MODEL)
	scala -classpath .:$(MODBAT) $(MODBAT) $(OPTION) test.$(MODEL)
cleanlog:
	find -name "*.log" | xargs rm -f
	find -name "*.err" | xargs rm -f
clean:
	make cleanlog
	find -name "*.class" | xargs rm -f
	find -name "paho*" | xargs rm -rf
