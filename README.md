-----------
Basic Steps
-----------

1. Install JDK8 (Oracle), and make sure JAVA_HOME is set correctly to point to this. Update gradle if it asks you.

1a. make sure you have a working android studio env (flowfence uses Android SDK Platform 22, build tools 22.0.1)
1b. Startup Android studio and let a gradle build proceed. Open project "flowfence"
We need to do this so that the studio system fixes up SDK dir locations etc.

2. Build flowfence.service manually from cmd line
cd flowfence.service
../gradlew assembleDebug

(if running for first time, it will download and install a bunch of stuff. This is okay.)

The flowfence.service APK will be inside build/outputs/apk

Install this apk manually to a device

adb install -r <apk_name>

3. Start Android Studio and launch flowfence.test
This will bring up a client config app where you can control various params like number of sandboxes etc.

At this point, flowfence (flowfence) is deployed and ready.

--------------------
Running a sample app
--------------------
1. Start with trying to run the flowfence.test app. This is currently where you can run basic perf tests.
flowfence.test contains sample Quarantined Modules (or QMs).

2. externalapps/ contains the original source code of the apps that were ported to the framework.

3. flowfence.skeleton contains examples on how to build apps with Quarentine Modules (also known as QMs)

-------------------
Running SmartLights
-------------------
1. Install LocationBeacon from externalapps/
2. Install SmartDevResponder from flowfence/ and click on Connect
3. Install PresenceBasedControl from flowfence/ and click on Connect
4. Navigate to LocationBeacon, click Login to FireBase. Wait for a toast that says login was OK
5. Click on Home and watch the logcat
6. Click on Away and watch the logcat

There should be messages indicating what is happening. You'd have to replace the URLs for your
own SmartThings setup if you want to see a switch physically toggle its state. Right now, we are
working on a more generic way to get this config done, but for now its manual...

--------------------------
Running SmartPlug app
--------------------------
1. Run flowfence.smartplug app located on flowfence/. 
2. Click on "Pair w/o or /w" to simulate pairing with a smart plug without/with Flowfence.
3. Click on "Toast Value" to  toast sensitive data written via Flowfence.
4. Click on "GET PLUG STATE/TURN OFF" to make network requests to a webserver (located on https://flowfence-testserver-211220.appspot.com/) which simulates a cloud manufacturer server responsible for controlling IoT devices in a IoT cloud architecture. The requests are made using FlowFence Network API enforcing policy to the endpoint manufacturer's URL only.

The app demonstrates implemented features on this forked version of FlowFence simulating a off-the-shelf Smart Plug app like Kasa (https://play.google.com/store/apps/details?id=com.tplink.kasa_android&hl=en_US). The idea is to present some case scenarios on the IoT Cloud Architecture and how to use FlowFence within this domain. Some already implemented features includes: 

* Possible to define sensitive UI data and enforce policy using FlowFence infrastructure.
* Network REST API implemented with fine-grained policies, i.e., you can now filter URL endpoints to a SOURCE -> NETWORK flow.



-------------------
Policy Files
-------------------
Right now, flowfence_manifest.xml inside xml/ in an app lists the flow policies. Currently, we only
have publisher policies and are working to commit some updates for consumer policies. If you see
the policy code, it is fairly straightforward to implement a consumer policy (i.e., you can add
such support yourself; we do accept pull requests!)

------------------
SmartThings Bridge
------------------

The SmartThings bridge enables flowfence to communicate with physical devices that are managed by
smartthings. We built a webservices smartapp that exposes various methods that can be called
remotely from the flowfence framework. This requires negotiating an OAuth token. The current code
directly embeds this token (which is unsafe). A more production ready implementation has to
negotiate this token at runtime and store it securely (e.g., encrypted under a password when at rest).

--------------------------
Miscellaenous Design Notes
--------------------------

FlowFence policy file is in Flowfenceervice/res/raw/flowfence_policy.xml
The policy is loaded when the service is started explicitly and/or when a client binds to the service. (flowfence behaves as a started service if started explicitly via the gui or as a bound service if it's started by the binding of a client).

Permissions are defined in FlowfenceCommon/src/com/temporary/flowfencecommon/FlowfencePerm.java, in the enumerator PermissionsEnum. 
Adding a new permissions is a matter of adding a field to the enum, and adding methods that use this permission in the DataGateway (both in the aidl interface and the implementation).
Permissions are managed with a BitSet (which is a set of bits, just to state the obvious): a 1 (set) bit means the usage of the relative permission.
This allows for quick policy checks (every entry in the policy allows,denies or require user intervention for one or more permissions, and that can be expressed with another BitSet: checking a policy entry is a matter of doing a boolean AND between the two sets and checking if the result is equal to the policy entry or not), is efficient and is not limited to the number of bits for the integer implementation.

The DataGateway currently offers only a couple of methods to access the imei, last known locations (both coarse and fine grained) and to send data via http POST.
I will add more methods while making BarcodeScanner use Flowfence.

I've added a "do test 1" button to the testclient: this call first gets the IMEI via flowfence, then uses the token to send the IMEI via http POST to a webserver (address and port specified as constants in FlowfenceTestClient/src/com/temporary/flowfencetestclient/MainActivity.java). Of course this is uses permissions IMEI+INTERNET that can be regulated via the policy file.

testserver.py is a sample webserver in python that prints the values sent.

STILL TODO:
 - sandbox pooling criteria
 - token lifecycle/allow encryption of tokens for storage
 - (eventual asynchronous interface for runQM [allowing clients to register a callback service to retrieve results])
