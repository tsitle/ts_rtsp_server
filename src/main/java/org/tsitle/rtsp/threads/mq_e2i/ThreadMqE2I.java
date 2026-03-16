package org.tsitle.rtsp.threads.mq_e2i;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.mq.MqExternalSub;
import org.tsitle.rtsp.mq.MqInternalPub;
import org.tsitle.rtsp.mq.MqPacketAv;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.MqException;
import org.tsitle.rtsp.helpers.CancelToken;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RunnableBase;

import java.net.URI;
import java.util.Optional;

public class ThreadMqE2I extends RunnableBase {

	private final String threadName;

	private final @NonNull MqExternalSub mqExternalSub;
	private final @NonNull MqInternalPub mqInternalPub;

	private final @NonNull BufferExt cachePayloadData = new BufferExt();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param streamSourceId Stream source identifier
	 * @param mqUri URI of the Message queue (User:Password + IP/hostname + port + path)
	 */
	public ThreadMqE2I(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				int streamSourceId,
				@NonNull URI mqUri
			) {
		super(logMsgInterface, cancelToken);

		//
		if (mqUri.getScheme() == null) {
			throw new IllegalArgumentException("Input URI scheme cannot be null (mqUri='" + mqUri + "')");
		}
		if (! mqUri.getScheme().equals("tcp")) {
			throw new IllegalArgumentException("Unsupported input URI scheme: " + mqUri.getScheme());
		}
		if (mqUri.getUserInfo() == null || mqUri.getUserInfo().isEmpty()) {
			throw new IllegalArgumentException("Missing authentification in input URI: " + mqUri);
		}
		if (mqUri.getPort() == -1) {
			throw new IllegalArgumentException("Missing port in input URI: " + mqUri);
		}

		this.threadName = "MQE2I#" + streamSourceId;

		//
		mqExternalSub = new MqExternalSub(
				logMsgInterface, mqUri.getHost() + ":" + mqUri.getPort(), mqUri.getPath(), mqUri.getUserInfo()
			);
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
			mqExternalSub.connectToMq();
			mqInternalPub.connectToMq();

			//
			while (! hasBeenRequestedToStop()) {
				mainLoop();
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
			mqExternalSub.close();
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException, MqException {
		Thread.sleep(1);

		Optional<MqPacketAv> optPack = mqExternalSub.receiveMessage(cachePayloadData);
		if (optPack.isEmpty()) {
			return;
		}
		mqInternalPub.sendMessage(optPack.get());
	}

}
