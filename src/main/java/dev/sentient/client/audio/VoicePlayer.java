package dev.sentient.client.audio;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import dev.sentient.Sentient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Plays server-sent speech with distance attenuation and simple stereo panning.
 * (v1 uses the JVM audio path; OpenAL positional playback is a later upgrade.)
 */
public final class VoicePlayer {

    private static final double MAX_DISTANCE = 32.0;
    private static final int MAX_CONCURRENT = 4;
    private static final AtomicInteger ACTIVE = new AtomicInteger();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "sentient-voice");
        t.setDaemon(true);
        return t;
    });

    private VoicePlayer() {
    }

    public static void play(int entityId, byte[] wav) {
        if (wav == null || wav.length == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float gain = (float) Sentient.config().ttsVolume;
        float pan = 0f;

        Entity e = mc.level.getEntity(entityId);
        if (e != null && mc.player != null) {
            double dist = Math.sqrt(e.distanceToSqr(mc.player));
            if (dist > MAX_DISTANCE) {
                return;
            }
            gain *= (float) Math.max(0.0, 1.0 - dist / MAX_DISTANCE);
            pan = computePan(mc, e);
        }

        if (gain <= 0.01f || ACTIVE.get() >= MAX_CONCURRENT) {
            return;
        }
        final float fGain = gain;
        final float fPan = pan;
        ACTIVE.incrementAndGet();
        POOL.execute(() -> {
            try {
                playClip(wav, fGain, fPan);
            } catch (Exception ex) {
                Sentient.LOGGER.debug("Voice playback failed: {}", ex.toString());
            } finally {
                ACTIVE.decrementAndGet();
            }
        });
    }

    private static float computePan(Minecraft mc, Entity e) {
        try {
            Vec3 eye = mc.player.getEyePosition(1.0f);
            Vec3 to = e.position().subtract(eye);
            if (to.lengthSqr() < 1.0e-4) {
                return 0f;
            }
            to = to.normalize();
            Vec3 look = mc.player.getViewVector(1.0f);
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() < 1.0e-4) {
                return 0f;
            }
            flat = flat.normalize();
            Vec3 right = new Vec3(-flat.z, 0, flat.x);
            return (float) Math.max(-1.0, Math.min(1.0, to.dot(right)));
        } catch (Exception ex) {
            return 0f;
        }
    }

    private static void playClip(byte[] wav, float gain, float pan) throws Exception {
        try (AudioInputStream src = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            AudioFormat inFormat = src.getFormat();
            int channels = Math.max(1, inFormat.getChannels());
            float rate = inFormat.getSampleRate();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels,
                    channels * 2, rate, false);
            AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcm, src);
            byte[] data = pcmStream.readAllBytes();

            int frames = data.length / (2 * channels);
            short[] samples = new short[frames * channels];
            for (int i = 0; i < samples.length; i++) {
                int lo = data[i * 2] & 0xff;
                int hi = data[i * 2 + 1];
                samples[i] = (short) ((hi << 8) | lo);
            }

            double angle = (pan + 1.0) * (Math.PI / 4.0);
            float leftGain = (float) (Math.cos(angle) * gain);
            float rightGain = (float) (Math.sin(angle) * gain);

            byte[] out = new byte[frames * 4];
            for (int f = 0; f < frames; f++) {
                short l;
                short r;
                if (channels >= 2) {
                    l = samples[f * channels];
                    r = samples[f * channels + 1];
                } else {
                    l = samples[f];
                    r = samples[f];
                }
                int ol = clamp16((int) (l * leftGain));
                int or = clamp16((int) (r * rightGain));
                int o = f * 4;
                out[o] = (byte) (ol & 0xff);
                out[o + 1] = (byte) ((ol >> 8) & 0xff);
                out[o + 2] = (byte) (or & 0xff);
                out[o + 3] = (byte) ((or >> 8) & 0xff);
            }

            AudioFormat outFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, 2, 4, rate, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, outFormat);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(outFormat);
                line.start();
                line.write(out, 0, out.length);
                line.drain();
                line.stop();
            }
        }
    }

    private static int clamp16(int v) {
        return v < -32768 ? -32768 : (v > 32767 ? 32767 : v);
    }
}
