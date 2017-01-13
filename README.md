-----------
Intro
-----------
A previous iteration of this system was called OASIS. We will refactor class names to 
FlowFence and update the repo soon. For now, all of the classes will be named "oasis"

Terminology Map (this will not be needed after refactor is complete):
Quarantined Module (QM in FlowFence): Sensitive Operation Defined By The Application (SODA in OASIS)

The refactor will simply change SODA to QM and OASIS to FlowFence

-----------
Basic Steps
-----------

1. Install JDK8 (Oracle), and make sure JAVA_HOME is set correctly to point to this. Update gradle if it asks you.

1a. make sure you have a working android studio env (flowfence uses Android SDK Platform 22, build tools 22.0.1)
1b. Startup Android studio and let a gradle build proceed. Open project "oasis2"
We need to do this so that the studio system fixes up SDK dir locations etc.

2. Build oasis.service manually from cmd line
cd oasis.service
../gradlew assembleDebug

(if running for first time, it will download and install a bunch of stuff. This is okay.)

The oasis.service APK will be inside build/outputs/apk

Install this apk manually to a device

adb install -r <apk_name>

3. Start Android Studio and launch oasis.test
This will bring up a client config app where you can control various params like number of sandboxes etc.

At this point, flowfence (oasis) is deployed and ready.

--------------------
Running a sample app
--------------------
1. Start with trying to run the oasis.test app. This is currently where you can run basic perf tests.
oasis.test contains sample Quarantined Modules (or SODAs).

2. externalapps/ contains the original source code of the apps that were ported to the framework.

------------------
SmartThings Bridge
------------------

The SmartThings bridge enables oasis to communicate with physical devices that are managed by
smartthings. We built a webservices smartapp that exposes various methods that can be called
remotely from the oasis framework. This requires negotiating an OAuth token. The current code
directly embeds this token (which is unsafe). A more production ready implementation has to
negotiate this token at runtime and store it securely (e.g., encrypted under a password when at rest).

--------------------------
Miscellaenous Design Notes
--------------------------

OASIS policy file is in OASIService/res/raw/oasis_policy.xml
The policy is loaded when the service is started explicitly and/or when a client binds to the service. (oasis behaves as a started service if started explicitly via the gui or as a bound service if it's started by the binding of a client).

Permissions are defined in OASISCommon/src/com/temporary/oasiscommon/OASISPerm.java, in the enumerator PermissionsEnum. 
Adding a new permissions is a matter of adding a field to the enum, and adding methods that use this permission in the DataGateway (both in the aidl interface and the implementation).
Permissions are managed with a BitSet (which is a set of bits, just to state the obvious): a 1 (set) bit means the usage of the relative permission.
This allows for quick policy checks (every entry in the policy allows,denies or require user intervention for one or more permissions, and that can be expressed with another BitSet: checking a policy entry is a matter of doing a boolean AND between the two sets and checking if the result is equal to the policy entry or not), is efficient and is not limited to the number of bits for the integer implementation.

The DataGateway currently offers only a couple of methods to access the imei, last known locations (both coarse and fine grained) and to send data via http POST.
I will add more methods while making BarcodeScanner use OASIS.

I've added a "do test 1" button to the testclient: this call first gets the IMEI via oasis, then uses the token to send the IMEI via http POST to a webserver (address and port specified as constants in OASISTestClient/src/com/temporary/oasistestclient/MainActivity.java). Of course this is uses permissions IMEI+INTERNET that can be regulated via the policy file.

testserver.py is a sample webserver in python that prints the values sent.

STILL TODO:
 - sandbox pooling criteria
 - token lifecycle/allow encryption of tokens for storage
 - (eventual asynchronous interface for runSODA [allowing clients to register a callback service to retrieve results])
