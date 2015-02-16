# DVSDSE - Dynamic, Very Simple Drools Standalone Engine
__ONE JAVA CLASS TO RULE THEM ALL!__

This project is intented to create a very/as much as simple, but also dynamic __drools engine__ based on a XSD definition and XML with a minimum on dependencies and learning-curve.

Currently (un)marshalling of objects and XML is done by [EclipseLink (MOXy)](http://eclipse.org/eclipselink/moxy.php).


## Getting started
Since there is currently no binary distribution available which would create a reasonable use-case you need to clone this repository and call the _build.xml_ via [ANT](https://ant.apache.org/) this should work on any operating system available and create a executable _dvsde.jar_ in the _build_-directory for testing purposes.

For linux there is a _run.sh_ shell-script which does all the steps for you.

So those are the steps
### clone this repository and run ant
```
~# git clone https://github.com/kai-wegner/DVSDSE.git
~# cd DVSDSE
~/DVSDSE# ant
```
### go to the build directory and execute the dvsdse.jar
```
~/DVSDSE# cd build
~/DVSDSE/build# java -jar dvsdse.jar
```

## How does it work (functional aspects)
After building and running the _dvsdse.jar_ (see above) the program runs two test scenarios which produce the following output:
```
~/DVSDSE/build# java -jar dvsdse.jar
########## First test with programatic overrides - uses 600k with score < 90.1
(Un)Marshalled credit application sucessfully:
<?xml version="1.0" encoding="UTF-8"?><amount>600000</amount><applicant><name>Andreas Roppel</name><creditScore>99.0</creditScore></applicant>---

# Found: CreditCheck_0.0.1.xls will load: src/main/resources/standaloneSession/CreditCheck_0.0.1.xls
# No Errors found!

Starting to insert Objects into KIE-Session ...
... done (took: 18 ms)!
Starting Rule Execution...
... done (took: 0 ms)!
Calculated RiskScore for "Andreas Roppel": not granted
########## Expected Result: Credit not granted for Andreas Roppel

########## Second test without programatic overrides - uses ./examples/CreditApplication_500k.xml
(Un)Marshalled credit application sucessfully:
<?xml version="1.0" encoding="UTF-8"?><amount>500000</amount><applicant><age>31</age><name>Kai Wegner</name><creditScore>99.8</creditScore><foreignCredits>2</foreignCredits><payoffCredits>0</payoffCredits></applicant>---

# Found: CreditCheck_0.0.1.xls will load: src/main/resources/standaloneSession/CreditCheck_0.0.1.xls
# No Errors found!

Starting to insert Objects into KIE-Session ...
... done (took: 30 ms)!
Starting Rule Execution...
Granted a credit for: Kai Wegner
... done (took: 6 ms)!
Calculated RiskScore for "Kai Wegner": 30.0
########## Expected Result: RiskScore of 30.0 for Kai Wegner
```
### How do the test scenarios work in general?
The the _build-directory_ contains a _examples-directory_ which contains all necessary files to run the tests.
This directory has two mandatory files and one mandatory directory. You may not remove the files or either the directory without changing the code, since those are not configurable at the moment.

__CreditApplication.xsd__ contains the domain model for the test. All objects and attributes you can use later on are defined here. You may change this file to add addtional attributes or objects you want to use in you own example or test.

__CreditApplication_500k.xml__ contains the payload or the instances of the domain model. You may change this file to change the values for the second test scenario only, since the first test scenario overwrites the values in this file via code.
Per default it contains a _$500.000_ credit application with a applicant which has a credit score of _99.8_.

__excel-file__ this directory contains all rules which should be availbale to the drools engine, based on the filename _CreditCheck_0.0.1.*_. If you want to change the rule-name _CreditCheck_ or the rule-version _0.0.1_ you need to change it in the code, since those are not configurable at the moment.

For example: You can put an additional _CreditCheck_0.0.1.drl_ into this directory which will be loaded. Putting a file named _MyRuleCheck_0.0.2.xls_ into this directory will not work without changing and recompiling the code.

#### Both scenarios will
1. create (unmarshall) java objects out of the domain model provided in _examples/CreditApplication.xsd_ and _examples/CreditApplication_500k.xml_
2. unmarshall and print out the generated objects
3. load all rules in _examples/excel-file_
4. build a dynamic _KIE-Module_ and create a _KIE-Session_
5. checks for errors in the _KIE-Module_ and handles them accordingly
6. call _fireAllRules()_ of the _KIE-Session_
7. check the result and print out some details

### How to change the rules in both scenarios?
Check the provided _examples/excel-file/CreditApplication_0.0.1.xls_.
Every row is a rule and every column is either a condition or action.
Find the documentation about how to use decision-tables in spreadsheets within drools [here](http://docs.jboss.org/drools/release/5.5.0.Final/drools-expert-docs/html_single/#d0e1157).

Since we are working with _DynamicEntities_ (see the documentation [here](http://docs.oracle.com/middleware/1212/toplink/TLJAX/dynamic_jaxb.htm#TLJAX450)) rather than with real (compiled) classes, the syntax within the condition and action columns may feel a little clumsy and dangerous - but that's the downside of having everything dynamic, right?

You can use functions to make the life of your rule-developers a bit easier. I did not. Check out the [documentation](http://docs.jboss.org/drools/release/5.5.0.Final/drools-expert-docs/html_single/#d0e1309) of drools.
Drools and EclipseLink take care (at runtime) that everything in the spreadsheet is ok and the syntax is fine.

__No compilation__ when changing the rules (_CreditCheck_0.0.1.xls_), the domain model (_CreditApplication.xsd_) or payload (values of the domain model) (_CreditApplication_500k.xml_) is needed - simply re-run the _dvsdse.jar_!

### First Test scenario
#### Business Case
Get a risk score for a $600.000 credit application with a applicant which has a credit score of 99.0.

This credit application should not be granted, since credit applications above $250.000 (High Risk) need a applicant with a credit score more than 99.0.
### Technical Aspects
This scenario tests if a programmatic overwrite can be done.

If you want to change any values here you need to change and recompile the code.

### Second Test scenario
#### Business Case
Get a risk score for a $500.000 credit application with a applicant which has a credit score of 99.8.

This credit application should be granted with a risk score of 30.0.
### Technical Aspects
This scenario tests if the marshalling and unmarshalling of XML / objects is working. It uses the _examples/CreditApplication_500k.xml_ as data-source.

If you want to change any values here you need to change the file _examples/CreditApplication_500k.xml_ and simply re-run the _dvsdse.jar_.

## Technical Details
Inspect the source you must, Luke.
