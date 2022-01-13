Engine_Johann : CroneEngine {

    var voices;
    var voiceGroup;
    var params;
    var diskPlayerDef;
    var loopBufs;

    var maxVoices = 32;

	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

	alloc {
        voices = List.newClear();
        voiceGroup = Group.new(context.xg);

        params = (
            \release: 10,
            \rate: 1
        );

        //synthdef for multisample player
        diskPlayerDef = SynthDef(\diskPlayer,{

            //sustain-relase envelope
            var env = EnvGen.kr(
                Env.asr( 0, 1, \release.kr()),
                \gate.kr(),
                doneAction: Done.freeSelf
            );

            //envelope for killing the voice
            var killGate = \killGate.kr() + Impulse.kr(0);
			var killEnv = EnvGen.kr(
                Env.asr( 0, 1, 0.01),
                \killGate.kr(),
                doneAction: Done.freeSelf
            );

            //disk reading Ugen using the cue buffer
            var diskin = VDiskIn.ar(2, \bufnum.kr(), \rate.kr());
            FreeSelfWhenDone.kr(diskin);

            Out.ar(0, diskin * env * killEnv);
        }).add;

        context.server.sync;

        //TODO: stereo / mono option.
        //engine.noteOn(<id>, <sample path>)
        this.addCommand("noteOn", "is", { arg msg;
            var id = msg[1];
            var path = msg[2];

            //kill oldest voices while max voice count is exceeded
            if(voices.size >= maxVoices, {
                var voice = voices.last;

                if(voice.notNil && voice.theSynth.isPlaying, {
                    voice.theSynth.set(\gate, 0);
                    voice.theSynth.set(\killGate, 0);
                });
            });

            context.server.makeBundle(nil, {
                var buf, newVoice;

                //cue a new buffer for disk reading
                buf = Buffer.cueSoundFile(context.server, path, 0, 2);

                context.server.sync;

                //create a new disk player synth to read from the cue buffer
                newVoice = (
                    id: id,
                    theSynth: Synth(
                        \diskPlayer,
                        [\bufnum, buf, \gate, 1, \killGate, 1] ++ params.getPairs,
                        target: voiceGroup
                    ).onFree({
                        ("freeing: " ++ path).postln;

                        //free buffer & remove voice from voices list on free
                        buf.close();
                        buf.free();
                        voices.remove(newVoice);
                    })
                );

                NodeWatcher.register(newVoice.theSynth);
                voices.addFirst(newVoice);
            });
        });

        this.addCommand("noteOff", "i", { arg msg;
            var id = msg[1];
            var voice = voices.detect{arg v; v.id == id};

            if(voice.notNil && voice.theSynth.isPlaying, {
				voice.theSynth.set(\gate, 0);
			});
        });

		this.addCommand(\noteOffAll, "", { arg msg;
			voiceGroup.set(\gate, 0);
		});

        this.addCommand(\noteKillAll, "", { arg msg;
			voiceGroup.set(\gate, 0);
			voiceGroup.set(\killGate, 0);
		});

        //add all other commands via the `params` dict
        params.keysValuesDo({ arg k;
            this.addCommand(k, "f", { arg msg;
                params[k] = msg[1];
                voiceGroup.set(k, msg[1]);
            });
        });
	}

	free {
		voiceGroup.free;
	}
}