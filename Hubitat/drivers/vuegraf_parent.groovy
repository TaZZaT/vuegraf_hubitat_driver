/*
 * Emporia Vue Driver - Install/Configure vuegraf.py, then run InfluxVuePull.py
 *
 *  Copyright 2021 Paul Nielsen
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

 *
 *
 *  v1.0 - Initial 02-26-2021
 *
 */

import groovy.json.JsonSlurper

metadata {
    definition(name: 'Emporia Vue Parent', namespace: 'pnielsen', author: 'Paul Nielsen') {
        capability "Presence Sensor"
		capability "PowerMeter"
		capability "Sensor"


        attribute 'status', 'string'
    }
}

preferences {
    input name: 'vueIP', type: 'string', title:'<b>Vue Proxy IP Address</b>', description: '<div><i>Please use a static IP.</i></div><br>', required: true
    input name: 'loggingEnabled', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 30 minutes.</i></div><br>', defaultValue: false
}

def installed() {
    log.debug 'VueGraf Data device installed'
    installedUpdated()
}

def uninstalled() {
    log.info "Executing VueGraf Data device 'uninstalled()'"
    unschedule()
    deleteAllChildDevices()
}



def updated() {
    installedUpdated()
}

void installedUpdated() {
    unschedule()

    state.remove('connectionStatus')

    setNetworkAddress()

    // disable logging in 30 minutes
    if (settings.loggingEnabled) runIn(1800, disableLogging)

    // perform health check every 1 minutes
    runEvery1Minute('healthCheck') 
}

// parse events into attributes
def parse(String description) {
    logDebug "Parsing '${description}'"

    def msg = parseLanMessage(description)   
    def bodyString = new groovy.json.JsonSlurper().parseText(msg.body)
                                                             
    if (bodyString) {
        logDebug "msg= ${bodyString}"        
        if (device.currentValue("presence") != "present") {
            sendEvent(name: "presence", value: "present", isStateChange: true, descriptionText: "New update received from VueGraf")
        }
    }  
    for (item in bodyString) {
      logDebug "bodyString.account.account_name = ${bodyString.account.account_name}"
      bodyString.account.channels.each {
        logDebug "Device ID = ${device.deviceNetworkId}-${it.channel_id}"
		def namenum = "${device.deviceNetworkId}-${it.channel_id}"
        def namebase = it.channel_name
        def value = it.usage


        
        def isChild = containsDigit(it.channel_id)

		def childDevice = null

		try {

            childDevices.each {
				try{
                 	if (it.deviceNetworkId == namenum) {
                	    childDevice = it
                        logDebug "Found a match!!!"
                	}
            	}
            	catch (e) {
                    log.error e
            	}
        	}
            
         	if (isChild && childDevice == null) {
        		logDebug "isChild = true, but no child found - Need to add!"
            	logDebug "    Need a ${namebase} with id = ${namenum}"
            
            	createChildDevice(namebase, namenum)
            	//find child after update
            	childDevices.each {
					try{
                		if (it.deviceNetworkId == namenum) {
                			childDevice = it
                    		logDebug "Found a match!!!"
                		}
            		}
            		catch (e) {
            			log.error e
            		}
        		}
        	}
            
            if (childDevice != null) {
                childDevice.parse("${value}")
				logDebug "${childDevice.deviceNetworkId} - name: ${namebase}, value: ${value}"
            }
            else { //this is a main meter
                float tmpValue = Float.parseFloat("${value}").round(1)
                sendEvent(name: 'power', value: tmpValue, unit: 'Watts')
            }
		}
                                
        catch (e) {
        	log.error "Error in parse(), error = ${e}"
        }       

                       
                        
     }
       
   }     
   state.lastReport = now()
   
}
    
void setNetworkAddress() {
    // Setting Network Device Id
    def dni = convertIPtoHex(settings.vueIP)
    if (device.deviceNetworkId != "$dni") {
        device.deviceNetworkId = "$dni"
        log.debug "Device Network Id set to ${device.deviceNetworkId}"
    }

    // set hubitat endpoint
    state.hubUrl = "http://${location.hub.localIP}:39501"
}

private void createChildDevice(String deviceName, String deviceNumber) {
    
		log.info "createChildDevice:  Creating Child Device '${device.displayName} (${deviceName}: ${deviceNumber})'"
        
		try {
        	def deviceHandlerName = "Emporia Vue Energy Meter Child"
            if (deviceHandlerName != "") {
         		addChildDevice(deviceHandlerName, "${deviceNumber}",
         			[label: "${deviceName} Power", 
                	 isComponent: false, 
                     name: "${device.displayName} (${deviceName}: ${deviceNumber})"])
        	}   
    	} catch (e) {
        	log.error "Child device creation failed with error = ${e}"
        	log.error "Child device creation failed. Please make sure that the '${deviceHandlerName}' is installed and published."
    	}
}


def deleteAllChildDevices() {
    log.info "Uninstalling all Child Devices"
    getChildDevices().each {
          deleteChildDevice(it.deviceNetworkId)
       }
}


void healthCheck() {
    if (state.lastReport != null) {
        // check if there have been any reports in the last 2 minute
        if(state.lastReport >= now() - (1 * 120 * 1000)) {
            // healthy
            logDebug 'healthCheck: healthy'
            sendEvent(name: 'status', value: 'Connected')
        }
        else {
            // not healthy
            log.warn "healthCheck: not healthy ${state.lastReport}"
            sendEvent(name: 'status', value: 'Disconnected')
        }
    }
    else {
        log.info 'No previous reports. Cannot determine health.'
    }
}

private boolean containsDigit(String s) {
    boolean containsDigit = false;

    if (s != null && !s.isEmpty()) {
		containsDigit = s.matches(".*\\d+.*")
        if (s.matches("1,2,3")) {
            containsDigit = false
        }
    }
    return containsDigit
}

private Integer convertHexToInt(hex) {
    return hex ? new BigInteger(hex[2..-1], 16) : 0
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex.toUpperCase()

}

void disableLogging() {
	log.info 'Logging disabled.'
	device.updateSetting('loggingEnabled',[value:'false',type:'bool'])
}

void logDebug(str) {
    if (loggingEnabled) {
        log.debug str
    }
}
