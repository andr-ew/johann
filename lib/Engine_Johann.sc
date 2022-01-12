Engine_Johann : CroneEngine {

    //dict[midival][dynamic][variation][release]

    var dynamics;
    var folder;

    var files;
    var cueBufs;
    var voices;

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

    //initiate cueBufs with all buffers cued with files
    cueAllBufs {
        cueBufs = Dictionary.new();

        files.keysValuesDo({ arg midival, dynamics;

            cueBufs[midival] ?? {
                cueBufs.put(midival, Dictionary.new());
            };

            dynamics.keysValuesDo({ arg dynamic, variations;

                cueBufs[midival][dynamic] ?? {
                    cueBufs[midival].put(dynamic, Dictionary.new());
                };

                variations.keysValuesDo({ arg variation, releases;

                    cueBufs[midival][dynamic][variation] ?? {
                        cueBufs[midival][dynamic].put(variation, Dictionary.new());
                    };

                    releases.keysValuesDo({ arg release, fileName;
                        var filePath = (folder +/+ fileName);
                        ("cuing: " ++ filePath).postln;

                        cueBufs[midival][dynamic][variation].put(release, Buffer.cueSoundFile(
                            context.server,
                            filePath,
                            0,
                            2
                        ));
                    })
                });
            });
        });
    }

    //free all cue buffers
    freeAllCueBufs {
        cueBufs.keysValuesDo({ arg midival, dynamics;
            // [midival: midival].postln;

            dynamics.keysValuesDo({ arg dynamic, variations;
                // [dynamic: dynamic].postln;

                variations.keysValuesDo({ arg variation, releases;
                    // [variation: variation].postln;

                    releases.keysValuesDo({ arg release, cueBuf;
                        // [release: release].postln;

                        ("freeing cue buffer: " ++ [
                            midival, dynamic, variation, release
                        ].join(".") ++ ".wav").postln;

                        cueBuf.free;
                    });
                });
            });
        });

        context.server.sync;
    }

	alloc {

        SynthDef(\diskPlayer,{
            var diskin = VDiskIn.ar(2, \bufnum.kr());
            FreeSelfWhenDone.kr(diskin);

            Out.ar(0, diskin);
        }).add;

        context.server.sync;

        voices = List.newClear();
        cueBufs = List.newClear();


        //engine.loadfolder(<absolute path to folder containing sample files>)
        this.addCommand("loadfolder", "s", { arg msg;
            this.fillFiles(msg[1].asString);
            //this.cueAllBufs();
        });

        //engine.noteOn(<midi_note>, <vel>, <variation>, <release>)
        this.addCommand("noteOn", "iiii", { arg msg;
            var midival = msg[1];
            var dynamic = msg[2];
            var variation = msg[3] ? 1;
            var release = msg[4] ? 0;

            var path = folder +/+ files[midival][dynamic][variation][release];

            var buf, x;

            context.server.makeBundle(nil, {
                buf = Buffer.cueSoundFile(context.server, path, 0, 2);
                context.server.sync;

                x = Synth(\diskPlayer, [\bufnum, buf]).onFree({

                    ("freeing: " ++ [
                        midival, dynamic, variation, release
                    ].join(".") ++ ".wav").postln;

                    buf.close();
                    buf.free();
                });
                NodeWatcher.register(x);
            });
        });
	}

	free {
        this.freeAllCueBufs();
	}
}
