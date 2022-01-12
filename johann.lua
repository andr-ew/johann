-- johann

engine.name = "Johann"

m = midi.connect()
m.event = function(data)
    local msg = midi.to_msg(data)
    if msg.type == "note_on" then
        
        -- args: midival, dynamic, variation, release
        engine.noteOn(msg.note, math.ceil((msg.vel/127)*4), 1, 0)
    elseif msg.type == "note_off" then
    end
end

function init()
    -- send the engine a folder of samples, naming format is the same as mx.samples

    engine.loadfolder(_path.audio .. 'johann/classic')
end

function cleanup() 
end
