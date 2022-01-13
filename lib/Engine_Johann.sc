Engine_Johann : CroneEngine {

    var dynamics;
    var folder;

    var files;
    var diskVoices;
    var voiceGroup;
    var params;
    var diskPlayerDef;

    var maxVoices = 32;

	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

    //fill the `files` array with filenames for each midival, dynamic, variation, release
    fillFiles { arg folderPath;
        folder = folderPath;

        files = Dictionary.new();
        PathName(folderPath).files.do({ arg file;
            var midival, dynamic, numberOfDynamics, variation, release;

            # midival, dynamic, numberOfDynamics, variation, release = (
                file.fileNameWithoutExtension.split($.)
            ).collect({ arg string;
                string.asInteger;
            });

            dynamics = numberOfDynamics;

            ((((files[midival] ?? {
                files.put(midival, Dictionary.new());
                files[midival];
            })[dynamic]) ?? {
                files[midival].put(dynamic, Dictionary.new());
                files[midival][dynamic];
            })[variation] ?? {
                files[midival][dynamic].put(variation, Dictionary.new());
                files[midival][dynamic][variation];
            }).put(release, file.fileName);
        });
    }

	alloc {
        diskVoices = List.newClear();
        voiceGroup = Group.new(context.xg);

        params = (
            \release: 10,
            \rate: 1
        );

        //synthdef for our regular multisample player (non-looping)
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
        //engine.loadfolder(<absolute path to folder containing sample files>)
        this.addCommand("loadfolder", "s", { arg msg;
            this.fillFiles(msg[1].asString);
        });

        //engine.noteOn(<midi_note>, <vel>, <variation>, <release>)
        this.addCommand("noteOn", "iiii", { arg msg;
            var midival = msg[1];
            var dynamic = msg[2];
            var variation = msg[3] ? 1;
            var release = msg[4] ? 0;

            var path = folder +/+ files[midival][dynamic][variation][release];

            //kill oldest voices while max voice count is exceeded
            if(diskVoices.size >= maxVoices, {
                var voice = diskVoices.last;

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

                //create a new synth
                newVoice = (
                    id: midival,
                    theSynth: Synth(
                        \diskPlayer,
                        [\bufnum, buf, \gate, 1, \killGate, 1] ++ params.getPairs,
                        target: voiceGroup
                    ).onFree({
                        ("freeing: " ++ [
                            midival, dynamic, variation, release
                        ].join(".") ++ ".wav").postln;

                        //free buffer & remove voice from voices list on free
                        buf.close();
                        buf.free();
                        diskVoices.remove(newVoice);
                    })
                );

                NodeWatcher.register(newVoice.theSynth);
                diskVoices.addFirst(newVoice);
            });
        });

        this.addCommand("noteOff", "i", { arg msg;
            var midival = msg[1];
            var voice = diskVoices.detect{arg v; v.id == midival};

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
