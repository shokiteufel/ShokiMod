package com.deeply.gankura.util;


import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays the user's own audio files for chat rules.
 *
 * <p>Minecraft's sound engine only knows sounds registered as {@code SoundEvent}s, so files a
 * user simply drops into a folder cannot go through it. This class decodes them with Java's own
 * audio stack instead and plays them on a background thread, the same approach SBO uses.
 *
 * <p>MP3 and Ogg Vorbis need decoders that are not part of the JDK. The service providers are
 * instantiated directly rather than discovered through {@link AudioSystem}, because service
 * discovery is unreliable under the mod loader's class loader.
 */
public final class CustomSoundPlayer {
	private static final Logger LOGGER = LoggerFactory.getLogger(CustomSoundPlayer.class);

	/** Drop your own files here. Created on first use so it is easy to find. */
	public static final Path SOUND_DIRECTORY = FabricLoader.getInstance().getConfigDir()
			.resolve("gankura").resolve("sounds");

	private static final List<String> SUPPORTED_EXTENSIONS = List.of("wav", "mp3", "ogg", "aiff", "aif", "au");

	/** One thread, so a long sound cannot pile up and never blocks the game thread. */
	private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "GanKura Custom Sound");
		thread.setDaemon(true);
		return thread;
	});

	/**
	 * What is playing right now, one entry per channel.
	 *
	 * <p>A channel is whoever asked for the sound - a single chat rule, or the preview button. Two
	 * different rules firing right after one another are meant to be heard on top of each other,
	 * otherwise the second alert would swallow the first. The same rule firing twice restarts
	 * instead, so it does not layer on itself.
	 *
	 * <p>Keeping one entry per channel also bounds how many mixer lines can be open at once.
	 */
	private static final Map<Object, Clip> ACTIVE_CLIPS = new ConcurrentHashMap<>();

	/** Channel for the preview in the sound picker, so trying files out never stacks up. */
	public static final Object PREVIEW_CHANNEL = new Object();

	private CustomSoundPlayer() {
	}

	/** Stops what this channel is playing, if anything. Other channels keep going. */
	public static void stop(Object channel) {
		close(ACTIVE_CLIPS.remove(channel));
	}

	private static void close(Clip clip) {
		if (clip == null) return;
		try {
			clip.stop();
			clip.close();
		} catch (Exception e) {
			LOGGER.debug("[GanKura Custom Sound] Could not stop a running clip", e);
		}
	}

	/** File names in the sound folder, sorted, for the config screen. */
	public static List<String> availableSounds() {
		try {
			if (!Files.isDirectory(SOUND_DIRECTORY)) {
				Files.createDirectories(SOUND_DIRECTORY);
				return List.of();
			}
			try (var stream = Files.list(SOUND_DIRECTORY)) {
				List<String> names = new ArrayList<>(stream
						.filter(Files::isRegularFile)
						.map(path -> path.getFileName().toString())
						.filter(CustomSoundPlayer::isSupported)
						.toList());
				names.sort(String.CASE_INSENSITIVE_ORDER);
				return names;
			}
		} catch (Exception e) {
			LOGGER.error("[GanKura Custom Sound] Failed to list {}", SOUND_DIRECTORY, e);
			return List.of();
		}
	}

	public static boolean isSupported(String fileName) {
		return SUPPORTED_EXTENSIONS.contains(extensionOf(fileName));
	}

	private static String extensionOf(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/**
	 * Plays a file from the sound folder. Does nothing if the file is gone, so a renamed or
	 * deleted sound never breaks the rule that references it.
	 *
	 * @param fileName name inside {@link #SOUND_DIRECTORY}
	 * @param volume   0.0 to 1.0
	 * @param channel  who is asking - a sound replaces only what the same channel is playing
	 */
	public static void play(String fileName, float volume, Object channel) {
		if (fileName == null || fileName.isBlank()) return;

		File file = SOUND_DIRECTORY.resolve(fileName).toFile();
		if (!file.isFile()) {
			LOGGER.warn("[GanKura Custom Sound] {} does not exist", file);
			return;
		}
		AUDIO_EXECUTOR.execute(() -> playBlocking(file, volume, channel));
	}

	private static void playBlocking(File file, float volume, Object channel) {
		// The same source firing twice restarts rather than layering on itself
		stop(channel);

		try (AudioInputStream encoded = openStream(file);
			 AudioInputStream decoded = toPcm(encoded, extensionOf(file.getName()))) {

			Clip clip = AudioSystem.getClip();
			clip.open(decoded);
			applyVolume(clip, volume);

			// Release the line as soon as playback ends, otherwise the mixer runs out of lines
			clip.addLineListener(event -> {
				if (event.getType() == LineEvent.Type.STOP) {
					clip.close();
					ACTIVE_CLIPS.remove(channel, clip);
				}
			});
			ACTIVE_CLIPS.put(channel, clip);
			clip.start();
		} catch (Exception e) {
			LOGGER.error("[GanKura Custom Sound] Failed to play {}", file.getName(), e);
		}
	}

	private static AudioInputStream openStream(File file) throws Exception {
		return switch (extensionOf(file.getName())) {
			case "mp3" -> new javazoom.spi.mpeg.sampled.file.MpegAudioFileReader().getAudioInputStream(file);
			case "ogg" -> new javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader().getAudioInputStream(file);
			default -> AudioSystem.getAudioInputStream(file);
		};
	}

	/** Clips need signed PCM; MP3 and Vorbis arrive in their own encoding. */
	private static AudioInputStream toPcm(AudioInputStream in, String extension) {
		AudioFormat source = in.getFormat();
		if (source.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) return in;

		AudioFormat target = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				source.getSampleRate(),
				16,
				source.getChannels(),
				source.getChannels() * 2,
				source.getSampleRate(),
				false);

		return switch (extension) {
			case "mp3" -> new javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider()
					.getAudioInputStream(target, in);
			case "ogg" -> new javazoom.spi.vorbis.sampled.convert.VorbisFormatConversionProvider()
					.getAudioInputStream(target, in);
			default -> AudioSystem.getAudioInputStream(target, in);
		};
	}

	/** Master gain is in decibels, so a linear slider has to be converted. */
	private static void applyVolume(Clip clip, float volume) {
		if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;

		FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
		float clamped = Math.clamp(volume, 0.0f, 1.0f);
		if (clamped <= 0.0f) {
			control.setValue(control.getMinimum());
			return;
		}
		float decibels = (float) (20.0 * Math.log10(clamped));
		control.setValue(Math.clamp(decibels, control.getMinimum(), control.getMaximum()));
	}
}
