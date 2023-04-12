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
    controlspec = cs.def{ min = 0, max = 15, default = 4 },
    action = function(v) engine.level(v) end,
}
params:add{
    id = 'rate', type = 'control',
    controlspec = cs.def{ 
        min = -2, max = 2, default = -0.12, quantum = 1/100/4,
    },
    action = function(v) engine.rate(2^v) end,
}
local notes = { 'C','C#','D','D#','E','F','F#','G','G#','A','A#','B', }
params:add{
    id = 'grid root', type = 'option',
    options = notes,
}

m = midi.connect()
m.event = function(data)
    if not get_passthrough() then
        local msg = midi.to_msg(data)
        if msg.type == "note_on" then
            
            -- args: midival, dynamic, variation, release
            engine.noteOn(msg.note-12, math.ceil((msg.vel/127)*7), 1, 0)
        elseif msg.type == "note_off" then
        end
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

function get_passthrough()
    return params:get('monitor_level') > -inf
end

function set_passthrough(v)
    params:set('monitor_level', v and 0.0 or -inf)
    redraw()
end

--norns ui

x = {}
y = {}

local mar = { left = 2, top = 7, right = 2, bottom = 0 }
local w = 128 - mar.left - mar.right
local h = 64 - mar.top - mar.bottom

x[1] = mar.left
x[2] = 128/2
y[1] = mar.top
y[2] = mar.top + h*(1.5/8)
y[3] = mar.top + h*(5.5/8)
y[4] = mar.top + h*(7/8)

local e = {
    { x = x[1], y = y[1] },
    { x = x[1], y = y[3] },
    { x = x[2], y = y[3] },
}
local k = {
    { x = x[1], y = y[2] },
    { x = x[1], y = y[4] },
    { x = x[2], y = y[4] },
}

function redraw()
    screen.clear()

    screen.move(k[2].x, k[2].y)
    screen.level(get_passthrough() and 15 or 4)
    screen.text('passthrough')
        
    screen.update()
end

function key(n, z)
    if z==1 and n==2 then
        set_passthrough(not get_passthrough())        
    end
end

--grid ui

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
            x = { 1, 16 }, y = { 2, 8 },
            lvl = function(_, x, y)
                local iv = scale
                local deg = (x-1)%#iv+1

                return deg == 1 and { 4, 15 } or { 0, 15 }
            end,
            action = function(v, t, d, add, rem)
                local k = add or rem
                local iv = scale
                local oct = k.y-5 + (k.x-1)//#iv + 1
                local deg = (k.x-1)%#iv+1

                --local hz = root * 2^oct * 2^(deg/12)
                local note = root + (params:get('grid root') - 1) + (oct*12) + iv[deg] - 1

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
