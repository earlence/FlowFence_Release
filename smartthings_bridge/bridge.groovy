/**
 *  OasisBridge
 *
 *  Copyright 2017 Regents of the University of Michigan
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "OasisBridge",
    namespace: "edu.umich.oasis.bridge",
    author: "Earlence Fernandes",
    description: "Oasis Bridge",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true)


preferences {
	section("Config") {
		input "locks", "capability.lock", multiple: true
        input "switches", "capability.switch", multiple: true
	}
}

mappings {
    path("/locks") {
        action: [
            GET: "listAllLocks",
            PUT: "doOp",
        ]
    }
    path("/lockcontrol/:id/:cmd") {
    	action: [
        	GET: "lockctrl",
        ]
    }
    
    path("/switches") {
    	action: [
        	GET: "listAllSwitches",
        ]
    }
    path("/switchcontrol/:id/:cmd") {
    	action: [
        	GET: "switchctrl",
        ]
    }
    path("/switchstate/:id") {
    	action: [
        	GET: "getSwitchState",
        ]
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
		
}

def listAllLocks()
{
	def out = [:]
	locks.each { it -> out[it.device.name] = it.device.id }
    return out
}

def listAllSwitches()
{
	def out = [:]
    
    switches.each { it -> out[it.device.name] = it.device.id }
    return out
}

def doOp()
{
	def op = request.JSON?.op
    def id = request.JSON?.id
    def index = request.JSON?.index
    
    def theLock = null
    
    locks.each { it ->
    	if(it.device.id == id)
        {
        	theLock = it
        }
    }
    
    if(op == "setCode")
    {
    	def newCode = request.JSON?.newcode
        theLock?.setCode(index, newCode)
    }
    else if(op == "deleteCode")
    {
    	theLock?.deleteCode(index)
    }
}

def lockctrl()
{
	def devid = params.id
    def cmd = params.cmd
    
    log.debug "device id ${devid}"
    log.debug "cmd ${cmd}"
    
    def theLock = null
    
    locks.each { it ->
    	if(it.device.id == devid)
        {
        	theLock = it
        }
    }
    
    if(theLock != null)
    	log.debug "found lock"
    
    if(cmd == "lock")
    {
    	log.debug "attempting lock"
    	theLock?.lock()
    }
    else if(cmd == "unlock")
    {
    	log.debug "attempting unlock"
    	theLock?.unlock()
    }
}

def switchctrl()
{
	def devid = params.id
    def cmd = params.cmd
    
    log.debug "device id ${devid}"
    log.debug "cmd ${cmd}"
    
    def theSwitch = null
    
    switches.each { it ->
    	if(it.device.id == devid)
        {
        	theSwitch = it
        }
    }
    
    if(theSwitch != null)
    	log.debug "found switch"
        
    if(cmd == "on")
    	theSwitch?.on()
    else if(cmd == "off")
    	theSwitch?.off()
}

def getSwitchState()
{
	def devid = params.id
    
    def theSwitch = null
    
    switches.each { it ->
    	if(it.device.id == devid)
        {
        	theSwitch = it
        }
    }
 
 	//return current state
    return theSwitch?.currentSwitch
}