require 'sinatra'
require 'json'
require_relative 'smartplug'

fakePlug = SmartPlug.new(1, 'on')

get '/' do
    fakePlug.to_json
end

post '/' do
    state = params["state"]
    if !isValidState(state)
        "Please inform a 'state' param with either on or off value".to_json
    else
        fakePlug.state = state
        fakePlug.to_json
    end
end

def isValidState (state)
    return state != "" && (state.downcase == "on" || state.downcase == "off")
end
