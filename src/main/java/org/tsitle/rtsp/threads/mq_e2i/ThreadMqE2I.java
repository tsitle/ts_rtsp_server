package org.tsitle.rtsp.threads.mq_e2i;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.mq.MqExternalSub;
import org.tsitle.rtsp.mq.MqInternalPub;
import org.tsitle.rtsp.mq.mqdata.MqCodecSettings;
import org.tsitle.rtsp.mq.mqdata.MqPacketAv;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.CancelToken;
import org.tsitle.rtsp.mq.mqdata.MqPacketCodec;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RunnableBase;

import java.net.URI;
import java.util.Optional;

public class ThreadMqE2I extends RunnableBase {

	private final @NonNull CodecSettingsChangedFromMqInterface codecSettingsChangedFromMqInterface;
	private final int streamSourceId;
	private final @NonNull URI mqUri;
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
	 * @param mqUri URI of the Message queue (User:Password + IP/hostname + port + path)
	 * @param mqSslCertPath Path to the SSL certificate file (can be empty)
	 */
	public ThreadMqE2I(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull CodecSettingsChangedFromMqInterface codecSettingsChangedFromMqInterface,
				int streamSourceId,
				@NonNull URI mqUri,
				@NonNull String mqSslCertPath
			) {
		super(logMsgInterface, cancelToken);

		//
		this.codecSettingsChangedFromMqInterface = codecSettingsChangedFromMqInterface;
		this.streamSourceId = streamSourceId;
		//
		if (mqUri.getScheme() == null) {
			throw new IllegalArgumentException("Input URI scheme cannot be null (mqUri='" + mqUri + "')");
		}
		if (! mqUri.getScheme().equals("https")) {
			throw new IllegalArgumentException("Unsupported input URI scheme: " + mqUri.getScheme());
		}
		if (mqUri.getUserInfo() == null || mqUri.getUserInfo().isEmpty()) {
			throw new IllegalArgumentException("Missing authentification in input URI: " + mqUri);
		}
		this.mqUri = mqUri;
		this.mqSslCertPath = mqSslCertPath;

		this.threadName = "MQE2I#ss" + streamSourceId;

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

		//
		try {
			mqInternalPub.connectToMq();

			while (! hasBeenRequestedToStop()) {
				mqExternalSub = new MqExternalSub(
						logMsgInterface,
						mqUri.getHost() + ":" + mqUri.getPort(),
						mqUri.getPath(),
						mqUri.getUserInfo(),
						mqSslCertPath.strip()
					);
				try {
					mqExternalSub.connectToMq();
					//
					while (! (hasBeenRequestedToStop() || mqExternalSub.isClosed())) {
						mainLoop();
					}
				} catch (MqException e) {
					logError(FNC_NAME, "MqException caught: " + e.getMessage());
					//noinspection BusyWait
					Thread.sleep(1000);
				} finally {
					mqExternalSub.close();
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
				// samplerate and channel count should never actually change during a session - but we need to read it once
				cacheCodecSettings.audioSamplerate = (cacheCodecSettings.codec == MqPacketCodec.LPCM16_8K_MONO ? 8000 : null);
				cacheCodecSettings.audioChannels = (cacheCodecSettings.codec == MqPacketCodec.LPCM16_8K_MONO ? 1 : null);
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

}
