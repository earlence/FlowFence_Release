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