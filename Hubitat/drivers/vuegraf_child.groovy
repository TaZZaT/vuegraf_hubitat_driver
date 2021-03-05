/*
 *  Child Vue Energy Meter
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
 *  History:
 *
 *  v1.0 - Initial 02-26-2021
 *
 */
metadata {
	definition (name: "Emporia Vue Energy Meter Child", namespace: "pnielsen", author: "Paul Nielsen") {
		capability "PowerMeter"
		capability "Sensor"
	}
   
    preferences {
        input name: 'loggingEnabled', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 30 minutes.</i></div><br>', defaultValue: false
	}

}

def parse(value) {
    logDebug "parse(${value}) called"
    if (value) {
        float tmpValue = Float.parseFloat(value).round(1)
        sendEvent(name: 'power', value: tmpValue, unit: 'Watts')
    }
    else {
    	log.error "Missing value.  Cannot parse!"
    }
}

def installed() {
    updated()
}

def updated() {
    if (loggingEnabled) runIn(1800,disableLogging)
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
