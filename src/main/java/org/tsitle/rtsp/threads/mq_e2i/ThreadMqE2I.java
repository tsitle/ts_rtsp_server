package org.tsitle.rtsp.threads.mq_e2i;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspSsMq;
import org.tsitle.rtsp.mq.MqExternalSub;
import org.tsitle.rtsp.mq.MqInternalPub;
import org.tsitle.rtsp.mq.mqdata.MqCodecSettings;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.CancelToken;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RunnableBase;

import java.util.Optional;

public class ThreadMqE2I extends RunnableBase {

	private final @NonNull CodecSettingsChangedFromMqInterface codecSettingsChangedFromMqInterface;
	private final int streamSourceId;
	private final @NonNull RtspSsMq mqSettings;
	private final @NonNull String mqSslCertPath;

	private final String threadName;

	private @Nullable MqExternalSub mqExternalSub = null;
	private final @NonNull MqInternalPub mqInternalPub;

	private final @NonNull BufferExt cachePayloadData = new BufferExt();
	private final @NonNull MqCodecSettings cacheCodecSettings = new MqCodecSettings();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param streamSourceId Stream source identifier
	 * @param mqSettings Message Queue settings
	 * @param mqSslCertPath Path to the SSL certificate file (can be empty)
	 */
	public ThreadMqE2I(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull CodecSettingsChangedFromMqInterface codecSettingsChangedFromMqInterface,
				int streamSourceId,
				@NonNull RtspSsMq mqSettings,
				@NonNull String mqSslCertPath
			) {
		super(logMsgInterface, cancelToken);

		//
		this.codecSettingsChangedFromMqInterface = codecSettingsChangedFromMqInterface;
		this.streamSourceId = streamSourceId;
		//
		this.mqSettings = mqSettings.clone();
		this.mqSslCertPath = mqSslCertPath;

		this.threadName = String.format("MQE2I#ss%d#%s:%s:%s",
				streamSourceId, mqSettings.getHost(), mqSettings.getRscGroup(), mqSettings.getRscChannel());

		//
		mqInternalPub = new MqInternalPub(logMsgInterface, streamSourceId);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		Thread.currentThread().setName(threadName);

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		//
		try {
			mqInternalPub.connectToMq();

			int failCount = 0;
			while (! hasBeenRequestedToStop()) {
				mqExternalSub = new MqExternalSub(
						logMsgInterface,
						mqSettings,
						mqSslCertPath.strip()
					);
				try {
					mqExternalSub.connectToMq();
					//
					while (! (hasBeenRequestedToStop() || mqExternalSub.isClosed())) {
						mainLoop();
						failCount = 0;
					}
				} catch (MqException e) {
					if (++failCount > 60) {
						failCount = 60;
					}
					logError(FNC_NAME, "MqException caught: " + e.getMessage());
				} finally {
					mqExternalSub.close();
				}
				//
				if (failCount > 0) {
					sleepLongAndProsper(failCount);
				}
			}
		} catch (MqException e) {
			logError(FNC_NAME, "MqException caught: " + e.getMessage());
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			mqInternalPub.close();
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException, MqException {
		if (mqExternalSub == null) {
			throw new IllegalStateException("mqExternalSub is null");
		}
		Optional<MqPacketAv> optPacket = mqExternalSub.receiveMessageAv(cachePayloadData);
		if (optPacket.isEmpty()) {
			return;
		}
		MqPacketAv packet = optPacket.get();
		//
		boolean haveChanges = false;
		if (cacheCodecSettings.codec == null || cacheCodecSettings.codec.ordinal() != packet.codec().ordinal()) {
			// the codec should never actually change during a session - but we need to read it once
			cacheCodecSettings.codec = packet.codec();
			if (! packet.codec().isVideo()) {
				// samplerate and channel count should never actually change during a session - but we need to read them once
				cacheCodecSettings.audioSamplerate = packet.mdAudioSamplerate();
				cacheCodecSettings.audioChannels = packet.mdAudioChannelCount();
			}
			haveChanges = true;
		}
		if (packet.codec().isVideo() &&
				(cacheCodecSettings.videoFps == null || cacheCodecSettings.videoFps != packet.mdVideoFps())) {
			// the framerate can change during a session
			cacheCodecSettings.videoFps = packet.mdVideoFps();
			haveChanges = true;
		}
		if (haveChanges) {
			codecSettingsChangedFromMqInterface.onCodecSettingsChangedFromMq(streamSourceId, cacheCodecSettings);
		}
		//
		mqInternalPub.sendMessageAv(packet);
	}

	private void sleepLongAndProsper(int secs) {
		try {
			int ms = secs * 1000;
			while (ms > 0 && ! hasBeenRequestedToStop()) {
				//noinspection BusyWait
				Thread.sleep(100L);
				ms -= 100;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
	}

}
