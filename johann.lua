-- johann

pattern_time = require 'pattern_time'
cs = require 'controlspec'

nest = include 'lib/nest/core'
Grid = include 'lib/nest/grid'
of = include 'lib/nest/util/of'
multipattern = include 'lib/nest/util/pattern-tools/multipattern'
to = include 'lib/nest/util/to'
PatternRecorder = include 'lib/nest/examples/grid/pattern_recorder'

engine.name = "Johann"

params:add{
    id = 'level', type = 'control',
    controlspec = cs.def{ min = 0, max = 15, default = 1 },
    action = function(v) engine.level(v) end,
}

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
    
    params:read()
    params:bang()
end

function cleanup() 
    params:write()
end

local root = 48
local scale = { 0, 2, 4, 7, 9 } --maj pent

local pattern, mpat = {}, {}
for i = 1,5 do
    pattern[i] = pattern_time.new() 
    mpat[i] = multipattern.new(pattern[i])
end

local function App()

    local painissimo_mezzo = 1
    local _painissimo_mezzo = to.pattern(mpat, 'painissimo_mezzo', Grid.number, function()
        return {
            x = { 2, 3 }, y = 1, 
            state = { 
                painissimo_mezzo,
                function(v) painissimo_mezzo = v end
            }
        }
    end)
    local forte = 0
    local _forte = to.pattern(mpat, 'forte', Grid.momentary, function()
        return {
            x = 1, y = 1,
            state = { forte, function(v) forte = v end }
        }
    end)

    local _keys = to.pattern(mpat, 'keys', Grid.momentary, function() 
        return {
            x = { 1, 8 }, y = { 2, 8 },
            action = function(v, t, d, add, rem)
                local k = add or rem
                local iv = scale
                local oct = k.y-4 + k.x//(#iv+1)
                local deg = (k.x-1)%#iv+1

                --local hz = root * 2^oct * 2^(deg/12)
                local note = root + (oct*12) + iv[deg]

                if add then 
                    local dyn = forte > 0 and 5 or (
                        (painissimo_mezzo * 2) - 1
                    ) + math.random(0, 2)
                    engine.noteOn(note, util.clamp(1, 7, dyn), 1, 0)
                end
            end
        }
    end)

    local _patrec = PatternRecorder()

    return function()
        _painissimo_mezzo()
        _forte()
        _keys()

        _patrec{
            x = { 4, 8 }, y = 1, 
            pattern = pattern, varibright = false,
        }
    end
end

nest.connect_grid(App(), grid.connect())
